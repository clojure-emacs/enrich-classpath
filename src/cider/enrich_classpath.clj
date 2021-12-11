(ns cider.enrich-classpath
  (:refer-clojure :exclude [time])
  (:require
   [cemerick.pomegranate.aether]
   [cider.enrich-classpath.collections :refer [add-exclusions-if-classified divide-by ensure-no-lists flatten-deps maybe-normalize safe-sort]]
   [cider.enrich-classpath.locks :refer [read-file! write-file!]]
   [cider.enrich-classpath.logging :refer [debug info warn]]
   [cider.enrich-classpath.source-analysis :refer [bad-source?]]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.walk :as walk]
   [fipp.clojure])
  (:import
   (java.io File)
   (java.net InetAddress URI UnknownHostException)
   (java.util.concurrent ExecutionException)
   (java.util.regex Pattern)))

(def ^String cache-filename
  (-> "user.home"
      System/getProperty
      (File. ".enrich-classpath-cache")
      (str)))

(defn serialize
  "Turns any contained coll into a vector, sorting it.

  This ensures that stable values are peristed to the file caches."
  [x]
  {:pre  [(map? x)]
   :post [(vector? %)]}
  (->> x
       (mapv (fn outer [[k v]]
               [(ensure-no-lists k)
                (some->> v
                         (mapv (fn inner [[kk vv]]
                                 [(ensure-no-lists kk) (some->> vv
                                                                (map ensure-no-lists)
                                                                safe-sort
                                                                vec)]))
                         (safe-sort)
                         vec)]))
       safe-sort
       vec))

(defn deserialize
  "Undoes the work of `#'serialize`.

  Note that only certain vectors must be turned back into hashmaps - others must remain as-is."
  [x]
  {:post [(map? %)]}
  (assert (vector? x) (class x))
  (->> x
       (map (fn [[k v]]
              [k (if (and v (empty? v))
                   []
                   (some->> v
                            (map (fn [[kk vv]]
                                   [kk (some->> vv
                                                set)]))
                            (into {})))]))
       (into {})))

(defn safe-read-string [x]
  {:pre  [(string? x)]
   :post [(vector? %)]}
  (if (string/blank? x)
    (do
      (assert (not-any? (comp zero? byte) x)
              "No ASCII NUL characters should be persisted")
      [])
    (try
      (read-string x)
      (catch Exception e
        (throw (ex-info "Couldn't read-string this string" {:x x}))))))

(defn ppr-str [x]
  (with-out-str
    (fipp.clojure/pprint x)))

(defn make-merge-fn [cache-atom]
  {:pre [@cache-atom]}
  (fn [^String prev-val]
    {:pre  [(string? prev-val)]
     :post [(string? %)]}
    (let [old-map (-> prev-val safe-read-string deserialize)
          ks (keys old-map)
          new-map (merge old-map @cache-atom)
          serialized (serialize new-map)
          reserialized (deserialize serialized)]
      (doseq [k ks]
        (assert (find reserialized k)
                (str "Expected newly serialized value to not drop existing key: " (pr-str k)))
        (when (seq (get old-map k))
          (assert (seq (get reserialized k))
                  (str "Expected overriden key to not empty out  a previous value:" (pr-str {:k k
                                                                                             :o (get old-map k)
                                                                                             :r (get reserialized k)})))))
      (ppr-str serialized))))

(defn resolve-with-timeout! [coordinates repositories]
  {:pre [(vector? coordinates)
         (-> coordinates count #{1})]}
  (try
    (deref (future
             (cemerick.pomegranate.aether/resolve-dependencies :coordinates coordinates
                                                               :repositories repositories))
           ;; timing out should be very rare, it's not here for a strong reason
           27500
           ::timed-out)
    (catch ExecutionException e
      (-> e .getCause throw))))

(defn maybe-add-exclusions* [x]
  (->> x
       (walk/postwalk (fn [item]
                        (cond-> item
                          (and (vector? item)
                               (some #{:classifier} item))

                          add-exclusions-if-classified)))))

(def maybe-add-exclusions (memoize maybe-add-exclusions*))

(defn resolve! [cache-atom repositories classifiers x]
  (let [v (or (get @cache-atom x)
              (get @cache-atom (maybe-normalize x))
              (try
                (let [x (maybe-add-exclusions x)
                      _ (debug (str ::resolving " " (pr-str x)))
                      v (resolve-with-timeout! x repositories)
                      [x] x]
                  (if (= v ::timed-out)
                    (do
                      (info (str ::timed-out " " x))
                      [])
                    (let [matching-artifact? (and (find v x)
                                                  (-> x (get 3) classifiers))
                          good-artifact? (and matching-artifact?
                                              (not (bad-source? x)))]
                      (when good-artifact?
                        (info (str ::found " " (pr-str x))))
                      (if (and matching-artifact? (not good-artifact?))
                        (do
                          (info (str ::omitting-empty-source " " (pr-str x)))
                          [])
                        ;; ensure the cache gets set to something:
                        (doto v assert)))))
                (catch AbstractMethodError e
                  ;; Catches:

                  ;; "Tried to use insecure HTTP repository without TLS:
                  ;; This is almost certainly a mistake; for details see
                  ;; https://github.com/technomancy/leiningen/blob/master/doc/FAQ.md"

                  ;; (apparently it's a bit hard to add some kind of conditional for only catching *that* AbstractMethodError,
                  ;; but AbstractMethodErrors are rare enough that we can simply assume they have a single possible cause)
                  [])
                (catch Exception e
                  (if (#{(Class/forName "org.eclipse.aether.resolution.DependencyResolutionException")
                         (Class/forName "org.eclipse.aether.transfer.ArtifactNotFoundException")
                         (Class/forName "org.eclipse.aether.resolution.ArtifactResolutionException")}
                       (class e))
                    []
                    (do
                      (-> e .printStackTrace)
                      nil)))
                (finally
                  (debug (str ::resolved "  " (pr-str x))))))]
    (when v
      (swap! cache-atom assoc x v))
    v))

(defn derivatives [classifiers managed-dependencies memoized-resolve! [dep version & args :as original]]
  (let [version (or version
                    (->> managed-dependencies
                         (filter (fn [[a]]
                                   (= dep a)))
                         first
                         second))]
    (if-not version ;; handles managed-dependencies
      [original]
      (let [transitive (->> (memoized-resolve! [(assoc-in original [1] version)])
                            flatten-deps)]
        (->> transitive
             (mapcat (fn [[dep version :as original]]
                       (assert version (pr-str original))
                       (->> classifiers
                            (mapv (partial vector dep version :classifier))
                            (into [original])
                            (distinct)
                            (remove (comp nil? second))
                            (filter (fn [x]
                                      (->> (memoized-resolve! [x])
                                           flatten-deps
                                           (filter #{(maybe-add-exclusions x)})
                                           first
                                           some?))))))
             (distinct)
             (vec))))))

(defn matches-version? [deps [s-or-j-name s-or-j-version :as s-or-j]]
  (let [[_ matching-version :as matching] (->> deps
                                               (filter (fn [[matching-name]]
                                                         (= matching-name
                                                            s-or-j-name)))
                                               first)]
    (if matching
      (= s-or-j-version matching-version)
      true)))

(defn choose-one-artifact
  "Prevents Lein `:pedantic` faults by picking one source."
  [deps managed-dependencies equivalent-deps]
  {:post [(if %
            (contains? (set equivalent-deps) %)
            true)]}
  (let [pred (fn [xs [inner-dep inner-version classifier-keyword classifier]]
               {:pre [inner-dep
                      inner-version
                      (#{:classifier} classifier-keyword)
                      (string? classifier)]}
               (->> xs
                    (some (fn [[dep version]]
                            {:pre [dep]}
                            (and (= dep inner-dep)
                                 (= version inner-version))))))]
    (or (->> equivalent-deps
             (filter (partial pred deps))
             (first))
        (->> equivalent-deps
             (filter (partial pred managed-dependencies))
             (first))
        (->> equivalent-deps
             (sort-by second)
             (last)))))

(def parallelism-factor
  "A reasonable factor for parallel Maven resolution, which tries to maximise efficiency
  while keeping thread count low which seems detrimental for both us and the Maven servers."
  (if (find-ns 'lein-monolith.task.each)
    1
    4))

(defn acceptable-repository? [[_ {:keys [url] :as x}]]
  (and (->> x keys (not-any? #{:password}))
       ;; Some domains may be behind under a VPN we are disconnected from:
       (try
         (when-let [{:keys [host scheme]} (some-> url URI. bean)]
           (if-not (#{"https"} scheme)
             false
             (do
               (InetAddress/getByName host)
               true)))
         (catch UnknownHostException _
           false))))

(def tools-jar-path
  "tools.jar is useful for Orchard in JDK8."
  (memoize (fn []
             (let [tools-file (-> "java.home" System/getProperty (io/file ".." "lib" "tools.jar"))]
               (when (.exists tools-file)
                 (-> tools-file .getCanonicalPath))))))

(defn add [{:keys                                                      [repositories
                                                                        managed-dependencies
                                                                        java-source-paths
                                                                        resource-paths
                                                                        source-paths
                                                                        test-paths]
            {:keys               [classifiers]
             plugin-repositories :repositories
             :or                 {classifiers #{"javadoc" "sources"}}} :enrich-classpath
            :as                                                        project}]

  (debug (str [::classifiers classifiers]))

  (write-file! cache-filename (fn [s]
                                (or (not-empty s) "[]")))

  (let [classifiers (set classifiers)
        repositories (or (not-empty plugin-repositories)
                         repositories)
        repositories (into {}
                           (filter acceptable-repository?)
                           repositories)
        initial-cache-value (-> cache-filename read-file! safe-read-string deserialize)
        cache-atom (atom initial-cache-value)
        add-dependencies (fn [deps]
                           (let [memoized-resolve! (memoize (partial resolve! cache-atom repositories classifiers))
                                 additions (->> deps
                                                (divide-by parallelism-factor)
                                                (pmap (fn [work]
                                                        (->> work
                                                             (mapcat (partial derivatives
                                                                              classifiers
                                                                              managed-dependencies
                                                                              memoized-resolve!))
                                                             ;; ensure the work is done within the pmap thread:
                                                             (vec))))
                                                (apply concat)
                                                (distinct)
                                                (filter (fn [[_ _ _ x]]
                                                          (classifiers x)))
                                                (filter (partial matches-version? deps))
                                                (group-by (fn [[dep version _ classifier]]
                                                            [dep classifier]))
                                                (vals)
                                                (map (partial choose-one-artifact deps managed-dependencies))
                                                (mapv (fn [x]
                                                        (conj x :exclusions '[[*]]))))]
                             (when-not (= initial-cache-value @cache-atom)
                               (write-file! cache-filename
                                            (make-merge-fn cache-atom)))
                             ;; order can be sensitive
                             (into additions deps)))
        jdk8? (re-find #"^1\.8\." (System/getProperty "java.version"))
        add-tools? (and jdk8?
                        (tools-jar-path))]
    (cond-> project
      true       (update :dependencies add-dependencies)
      add-tools? (update :jar-exclusions conj (-> (tools-jar-path)
                                                  Pattern/quote
                                                  re-pattern))
      add-tools? (update :resource-paths (fn [rp]
                                           (into [(tools-jar-path)] rp)))
      (seq java-source-paths) (update :resource-paths (fn [rp]
                                                        (let [corpus (->> java-source-paths
                                                                          (filterv (fn [jsp]
                                                                                     (let [s #{jsp}]
                                                                                       (and (not-any? s rp)
                                                                                            (not-any? s source-paths)
                                                                                            (not-any? s test-paths))))))]
                                                          (if (seq corpus)
                                                            (into corpus rp)
                                                            rp)))))))

(defmacro time
  {:style/indent 1}
  [atom-obj expr]
  `(let [start# (. System (nanoTime))
         ret# ~expr]
     (reset! ~atom-obj (format "Completed in %.2f minutes." (-> System
                                                                (. (nanoTime))
                                                                (- start#)
                                                                (double)
                                                                (/ 1000000.0)
                                                                (/ 60000.0))))
     ret#))

(defn wrap-failsafe
  "Wraps `f` in a 'failsafe' way, protecting it against exceptions and overly slow executions."
  [f ^long timeout]
  {:pre [(ifn? f)
         (pos? timeout)
         (integer? timeout)]}
  (fn [project]
    (try
      (let [v (deref (future
                       (f project))
                     (* 1000 timeout)
                     ::timed-out)]
        (if-not (= v ::timed-out)
          v
          (do
            (warn (str ::timed-out))
            project)))
      (catch ExecutionException e
        (-> e .getCause .printStackTrace)
        project))))

(defn middleware
  [{{:keys [failsafe timeout]
     :or   {failsafe true
            timeout  215}} :enrich-classpath
    :as                    project}
   & args]
  (let [a (atom nil)
        f (cond-> add
            failsafe (wrap-failsafe timeout))
        v (time a
            (f project))]
    (debug @a)
    v))
