(ns cider.enrich-classpath.clojure
  (:require
   [cider.enrich-classpath :as enrich-classpath]
   [cider.enrich-classpath.jdk :as jdk]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.tools.deps :as tools.deps]
   [clojure.tools.deps.util.dir :refer [with-dir]])
  (:import
   (java.io File)))

(defn commandize [args clojure]
  (->> args
       (apply vector clojure)
       (string/join " ")))

(defn impl ^String [clojure deps-edn-filename pwd args shorten?]
  {:pre [(vector? args)]} ;; for conj
  (let [aliases (into #{}
                      (comp (mapcat (fn [^String s]
                                      (or (when (-> s (.startsWith "-A"))
                                            (-> s
                                                (string/replace #"-A:" "")
                                                (string/replace #"-A" "")
                                                (string/split #":")))
                                          (when (-> s (.startsWith "-M"))
                                            (-> s
                                                (string/replace #"-M:" "")
                                                (string/replace #"-M" "")
                                                (string/split #":"))))))
                            (map keyword))
                      args)
        args-max-index (-> args count dec)
        [extra-flag extra-value] (reduce-kv (fn [acc ^long i x]
                                              (let [j (inc i)]
                                                (when-let [v (and (<= j args-max-index)
                                                                  (#{"-Sdeps" (pr-str "-Sdeps")} (nth args i))
                                                                  (nth args j))]
                                                  (reduced [x v]))))
                                            nil
                                            args)
        main (some (fn [s]
                     (when (or (string/starts-with? s "-M")
                               (string/starts-with? s "\"-M"))
                       s))
                   args)
        deps-dir (io/file pwd)
        deps-filename (str (io/file pwd deps-edn-filename))
        {original-deps :deps
         :keys [paths libs :mvn/repos]
         {:keys [extra-paths main-opts classpath-overrides]
          calculated-jvm-opts :jvm-opts} :argmap
         :as basis} (with-dir deps-dir
                      ;; `with-dir` allows us to use relative directories unrelated to the JVM's CWD.
                      (tools.deps/create-basis {:aliases aliases
                                                :project deps-filename
                                                :extra (some-> extra-value edn/read-string)}))
        args (into []
                   (remove (hash-set main extra-flag extra-value))
                   args)
        main-opts (reduce-kv (fn [acc ^long i x]
                               (let [j (dec i)]
                                 (conj acc (cond-> x
                                             (and (>= j 0)
                                                  (#{"-e" (pr-str "-e")} (nth main-opts j))
                                                  x)
                                             pr-str))))
                             []
                             main-opts)
        classpath-overrides-keys (-> classpath-overrides keys set)
        classpath-overrides-vector (vec classpath-overrides-keys)
        ;; a dep like `incanter` with :exclusions can expand to :children like `incanter-<blah>` without inheriting those :exclusions.
        ;; so :exclusions wouln't be fully honored, which can alter the dep tree and bring all sorts of problems.
        ;; Prevent that:
        fine-grained-exclusions (atom {})
        calc-fine-grained-exclusions!
        (fn self [artifact-name {:keys [exclusions parents]} visited]
          (or (get @fine-grained-exclusions artifact-name)
              (let [parents (flatten (vec ;; vec is important to use flatten properly
                                      parents))
                    exclusions (vec (reduce into
                                            #{}
                                            (conj (mapv (fn [p]
                                                          (or (get @fine-grained-exclusions p)
                                                              (let [criterion (hash-set p artifact-name)]
                                                                (if (contains? @visited criterion)
                                                                  []
                                                                  (do
                                                                    (swap! visited conj criterion)
                                                                    (self p (get libs p) visited))))))
                                                        parents)
                                                  (vec exclusions))))]
                (swap! fine-grained-exclusions assoc artifact-name exclusions)
                exclusions)))
        _ (->> libs
               (run! (fn [[artifact-name m]]
                       (calc-fine-grained-exclusions! artifact-name m (atom #{})))))
        ;; these are the deps after resolving aliases, and `:local/root` references:
        maven-deps (into []
                         (keep (fn [[artifact-name {mv :mvn/version}]]
                                 (when (and mv
                                            (not (classpath-overrides-keys artifact-name)))
                                   [artifact-name mv :exclusions (into classpath-overrides-vector
                                                                       (get @fine-grained-exclusions
                                                                            artifact-name))])))
                         libs)
        other-deps (into []
                         (remove (fn [[_ {mv :mvn/version}]]
                                   mv))
                         libs)
        paths (into paths extra-paths)
        original-paths-set (set paths)
        original-deps-set (->> original-deps (map first) set)
        shortened-jar-signature (string/join File/separator
                                             ["mx.cider" "enrich-classpath" (jdk/digits-str)])
        {maven-dependencies :dependencies
         :keys [resource-paths]} (enrich-classpath/middleware {:dependencies maven-deps
                                                               :enrich-classpath {:shorten shorten?}
                                                               :resource-paths paths})
        {:keys [classpath]} (tools.deps/calc-basis {:paths paths
                                                    :mvn/repos repos
                                                    :classpath-overrides classpath-overrides
                                                    :deps (merge (->> maven-dependencies
                                                                      (map (fn [[k v marker classifier]]
                                                                             [(cond-> k
                                                                                (#{:classifier} marker)
                                                                                (str "$" classifier)

                                                                                true symbol)
                                                                              {:mvn/version v
                                                                               :exclusions (into classpath-overrides-vector
                                                                                                 (get @fine-grained-exclusions k))}]))
                                                                      (into {}))
                                                                 (->> other-deps
                                                                      (keep (fn [[dep m]]
                                                                              (when (seq (select-keys m [:git/url :git/sha :git/tag :sha :tag
                                                                                                         :local/root]))
                                                                                ;; use m (and not the select-keys result) to honor `:exclusions`:
                                                                                [dep m])))
                                                                      (into {})))})
        ;; Avoids
        ;; `WARNING: Use of :paths external to the project has been deprecated, please remove: ...`:
        classpath (->> resource-paths
                       (remove original-paths-set)
                       (map (fn [entry]
                              {entry {:path-key ::_}}))
                       (into classpath))
        classpath (->> classpath
                       (sort-by (fn [[^String entry {:keys [lib-name path-key]}]]
                                  {:pre [(or lib-name path-key)]}
                                  (let [original-path? (and path-key (original-paths-set entry))]
                                    (cond
                                      (and original-path?
                                           (-> entry (.contains "src")))
                                      [0 entry]

                                      (and original-path?
                                           (-> entry (.contains "test")))
                                      [1 entry]

                                      (and original-path?
                                           (-> entry (.contains "resource")))
                                      [3 entry]

                                      original-path?
                                      [2 entry]

                                      ;; Let the original Clojure .clj libs go before any other deps -
                                      ;; makes it less likely for other libs to overwrite Clojure stuff:
                                      (and lib-name
                                           (-> entry (.contains "/org/clojure/"))
                                           (not (-> lib-name str (.contains "$"))))
                                      [4 lib-name]

                                      (original-deps-set lib-name)
                                      [5 lib-name]

                                      (and lib-name
                                           (not (-> lib-name str (.contains "$"))))
                                      [6 lib-name]

                                      (and path-key
                                           (-> entry (.contains shortened-jar-signature)))
                                      [10 entry]

                                      (and path-key
                                           (-> entry (.contains "unzipped-jdk-sources")))
                                      [11 entry]

                                      path-key ;; JDK sources
                                      [7 entry]

                                      lib-name ;; artifacts with sources or javadocs
                                      [8 lib-name]

                                      true ;; shouldn't happen, anyway we leave something reasonable
                                      [9 (or lib-name path-key)]))))
                       (map first)
                       (string/join File/pathSeparator))]
    (-> (mapv pr-str args)
        (conj "-Sforce" "-Srepro" "-J-XX:-OmitStackTraceInFastThrow" "-J-Dclojure.main.report=stderr" "-Scp" classpath)
        (into (if (or (some jdk/javac-tree-like calculated-jvm-opts)
                      (jdk/jdk8?))
                []
                [(str "-J" jdk/javac-tree-opens)]))
        (into (if (or (some jdk/javac-code-like calculated-jvm-opts)
                      (jdk/jdk8?))
                []
                [(str "-J" jdk/javac-code-opens)]))
        (into (if-not main
                []
                (mapv (partial str "-J")
                      calculated-jvm-opts)))
        (into (if (seq main-opts)
                main-opts
                []))
        (commandize clojure))))

(defn -main [clojure pwd shorten & args]
  (let [shorten? (case shorten
                   "false" false
                   true)]
    (try
      (println (try
                 (impl clojure "deps.edn" pwd (vec args) shorten?)
                 (catch AssertionError e
                   (-> e .printStackTrace)
                   ;; args are pr-stred to match the format emitted by the main code path:
                   (commandize (mapv pr-str args) clojure))
                 (catch Exception e
                   (-> e .printStackTrace)
                   ;; args are pr-stred to match the format emitted by the main code path:
                   (commandize (mapv pr-str args) clojure))))
      (finally
        (shutdown-agents))))
  (System/exit 0))
