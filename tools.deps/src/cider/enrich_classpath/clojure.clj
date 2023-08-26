(ns cider.enrich-classpath.clojure
  (:require
   [cider.enrich-classpath :as enrich-classpath]
   [cider.enrich-classpath.jdk :as jdk]
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
                                      (when (-> s (.startsWith "-A"))
                                        (-> s
                                            (string/replace #"-A:" "")
                                            (string/replace #"-A" "")
                                            (string/split #":")))))
                            (map keyword))
                      args)
        deps-dir (io/file pwd)
        deps-filename (str (io/file pwd deps-edn-filename))
        {original-deps :deps
         :keys [paths libs :mvn/repos]
         {:keys [extra-paths]} :argmap
         :as basis} (with-dir deps-dir
                      ;; `with-dir` allows us to use relative directories unrelated to the JVM's CWD.
                      (tools.deps/create-basis {:aliases aliases
                                                :project deps-filename}))
        ;; these are the deps after resolving aliases, and `:local/root` references:
        deps (into []
                   (keep (fn [[artifact-name {mv :mvn/version}]]
                           (when mv
                             [artifact-name mv])))
                   libs)
        paths (into paths extra-paths)
        original-paths-set (set paths)
        original-deps-set (->> original-deps (map first) set)
        shortened-jar-signature (string/join File/separator
                                             [".mx.cider" "enrich-classpath" (jdk/digits-str)])
        {:keys [dependencies
                resource-paths]} (enrich-classpath/middleware {:dependencies deps
                                                               :enrich-classpath {:shorten shorten?}
                                                               :resource-paths paths})
        {:keys [classpath]} (tools.deps/calc-basis {:paths paths
                                                    :mvn/repos repos
                                                    :deps (->> dependencies
                                                               (map (fn [[k v marker classifier]]
                                                                      [(cond-> k
                                                                         (#{:classifier} marker)
                                                                         (str "$" classifier)

                                                                         true symbol)
                                                                       {:mvn/version v}]))
                                                               (into {}))})
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
