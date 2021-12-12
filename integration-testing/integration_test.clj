(ns integration-test
  (:refer-clojure :exclude [time])
  (:require
   [cider.enrich-classpath :as sut]
   [cider.enrich-classpath.collections :refer [divide-by]]
   [cider.enrich-classpath.jdk-sources :as jdk-sources]
   [cider.enrich-classpath.locks :as locks]
   [cider.enrich-classpath.logging :refer [info]]
   [clojure.java.io :as io]
   [clojure.java.shell :refer [sh]]
   [clojure.string :as string])
  (:import
   (java.io File)))

(def env (-> (into {} (System/getenv))
             (dissoc "CLASSPATH")
             ;; for Lein logging:
             (assoc "DEBUG" "true")
             (assoc "LEIN_JVM_OPTS" "-Dclojure.main.report=stderr")))

(def lein (->> [;; DeLaGuardo/setup-clojure (linux)
                (io/file "/opt" "hostedtoolcache" "Leiningen" "2.8.1" "x64" "bin" "lein")
                (io/file "/opt" "hostedtoolcache" "Leiningen" "2.9.4" "x64" "bin" "lein")
                ;; DeLaGuardo/setup-clojure (macOS)
                (io/file "/Users" "runner" "hostedtoolcache" "Leiningen" "2.8.1" "x64" "bin" "lein")
                (io/file "/Users" "runner" "hostedtoolcache" "Leiningen" "2.9.4" "x64" "bin" "lein")
                (io/file "/usr" "local" "bin" "lein") ;; github actions (generic Lein setup)
                (-> "user.home" ;; personal setup
                    (System/getProperty)
                    (io/file "bin" "lein-latest"))
                (-> "user.home" ;; standard
                    (System/getProperty)
                    (io/file "bin" "lein"))]
               (filter (memfn ^File exists))
               first
               str))

(assert (seq lein))

(def project-version (-> "project.clj"
                         slurp
                         read-string
                         (nth 2)))

(assert (string? project-version))

(defn prelude [x]
  (cond-> [x
           "with-profile" "-user,+test"

           "update-in"
           ":plugins" "conj" (str "[mx.cider/enrich-classpath \""
                                  project-version
                                  "\"]")
           "--"

           "update-in"
           ":" "assoc" ":enrich-classpath" "{:classifiers #{\"sources\"}}"
           "--"

           "update-in"
           ":middleware" "conj" "cider.enrich-classpath/middleware"
           "--"]))

(def vanilla-lein-deps
  (conj (prelude lein) "deps"))

(def deps-commands
  (sort ;; ensure stable tests
   {"aleph"         vanilla-lein-deps
    "amazonica"     vanilla-lein-deps
    "carmine"       vanilla-lein-deps
    "cassaforte"    vanilla-lein-deps
    "cider-nrepl"   vanilla-lein-deps
    "elastisch"     vanilla-lein-deps
    "http-kit"      vanilla-lein-deps
    "jackdaw"       vanilla-lein-deps
    "langohr"       vanilla-lein-deps
    "machine_head"  vanilla-lein-deps
    "metabase"      vanilla-lein-deps
    "monger"        vanilla-lein-deps
    "pallet"        vanilla-lein-deps
    "quartzite"     vanilla-lein-deps
    "riemann"       vanilla-lein-deps
    "welle"         vanilla-lein-deps
    ;; uses various plugins:
    "schema"        (with-meta vanilla-lein-deps
                      ;; something core.rrb-vector related
                      {::skip-in-newer-jdks true})
    ;; uses lein-parent:
    "trapperkeeper" vanilla-lein-deps
    ;; uses lein-parent:
    "jepsen/jepsen" vanilla-lein-deps
    ;; uses lein-tools-deps:
    "overtone"      vanilla-lein-deps
    ;; uses lein-sub, lein-modules:
    "incanter"      (reduce into [(prelude lein)
                                  ["sub" "do"]
                                  (prelude "install,")
                                  ["deps"]])
    ;; uses lein-sub:
    "icepick"       (with-meta (reduce into [(prelude lein)
                                             (prelude "sub")
                                             ["deps"]])
                      ;; Icepick seemingly relies on tools.jar, unavailable in JDK9+
                      {::skip-in-newer-jdks true})
    ;; uses lein-sub:
    "crux"          (reduce into [(prelude lein)
                                  (prelude "sub")
                                  ["deps"]])
    ;; uses lein-sub:
    "pedestal"      (reduce into [(prelude lein)
                                  (prelude "sub")
                                  ["deps"]])
    ;; uses lein-monolith:
    "sparkplug"     (with-meta (reduce into [(prelude lein)
                                             ["monolith"]
                                             (prelude "each")
                                             ["do"
                                              "clean,"
                                              "install,"
                                              "deps"]])
                      ;; something fipp-related (unrelated to our vendored version):
                      {::skip-in-newer-jdks true})}))

(def classpath-commands
  (into {}
        (map (fn [[id command]]
               {:pre [(-> command last #{"deps"})]}
               (let [idx (-> command count dec)]
                 [id, (-> command
                          (subvec 0 idx)
                          (conj "classpath"))])))
        deps-commands))

(def classpath-commands-for-java-source-paths-test
  (let [m (->> classpath-commands
               (filter (fn [[id]]
                         (let [f (io/file "integration-testing" id "project.clj")]
                           (assert (-> f .exists))
                           (->> f
                                slurp
                                string/split-lines
                                (some (fn [line]
                                        (re-find #".*:java-source-paths.*/java" line)))))))
               (into {}))]
    ;; machine_head declares :java-source-paths not backed by an actual directory:
    (dissoc m "machine_head")))

(assert (seq classpath-commands-for-java-source-paths-test))

(defmacro time
  {:style/indent 1}
  [id expr]
  `(let [start# (. System (nanoTime))
         ret# ~expr]
     (println (format "Ran %s in %.2f minutes." ~id (-> System
                                                        (. (nanoTime))
                                                        (- start#)
                                                        double
                                                        (/ 1000000.0)
                                                        (/ 60000.0))))
     ret#))

(def parallelism-factor
  (Long/parseLong (or (System/getenv "integration_test_parallelism")
                      "1")))

(defn submodule-dir ^File [id]
  {:pre [(seq id)]}
  (let [dir (io/file "integration-testing" id)]
    (assert (-> dir .exists) dir)
    (assert (> (count (file-seq dir))
               1)
            dir)
    dir))

(defn run-repos! [commands-corpus f]
  (assert (pos? parallelism-factor))
  (assert (seq commands-corpus))
  (->> commands-corpus

       (divide-by parallelism-factor)

       (pmap (fn [chunks]
               (->> chunks
                    (mapv (fn [[id command]]
                            (if (and (not (-> "java.version" System/getProperty (string/starts-with? "1.8")))
                                     (-> command meta ::skip-in-newer-jdks))
                              id
                              (let [dir (submodule-dir id)
                                    _ (info (str "Exercising " id " in " (-> dir
                                                                             .getCanonicalPath)))
                                    _ (info (pr-str command))
                                    {:keys [out exit err]} (time id
                                                                 (apply sh (into command
                                                                                 [:dir dir
                                                                                  :env env])))
                                    ok? (zero? exit)]
                                (assert ok? (when-not ok?
                                              [id
                                               exit
                                               (pr-str (-> out (doto println)))
                                               (pr-str (-> err (doto println)))]))
                                (let [checking-deps? (= commands-corpus deps-commands)
                                      checking-classpath? ((hash-set classpath-commands
                                                                     classpath-commands-for-java-source-paths-test)
                                                           commands-corpus)
                                      lines (cond->> out
                                              true string/split-lines

                                              checking-deps?
                                              (filter (fn [s]
                                                        (string/includes? s "cider.enrich-classpath")))

                                              checking-classpath?
                                              (take-last 1)

                                              (and (not checking-deps?)
                                                   (not checking-classpath?))
                                              (assert false))
                                      good (cond->> lines
                                             checking-deps?
                                             (filter (fn [s]
                                                       (string/includes? s "/found")))

                                             checking-classpath?
                                             first

                                             (and (not checking-deps?)
                                                  (not checking-classpath?))
                                             (assert false))
                                      bad (->> lines (filter (fn [s]
                                                               (string/includes? s "/could-not")
                                                               (string/includes? s "/timed-out")
                                                               ;; #{"sources"} is specified in `#'prelude`
                                                               (string/includes? s ":classifier \"javadoc\""))))
                                      timing (->> out
                                                  string/split-lines
                                                  (filter (partial re-find #"Completed in.*minutes\.")))]
                                  (assert (empty? bad)
                                          (pr-str [id bad]))
                                  (assert (-> timing count pos?))
                                  (f id good)
                                  (info (str id " - " (first timing)))
                                  id))))))))

       (apply concat)

       doall))

(defn classpath-test! []
  (info "Running `classpath-test!`")
  (letfn [(run [extra-profile]
            {:post [(-> % count pos?)]}
            (let [{:keys [out err exit]} (apply sh (reduce into [[lein
                                                                  "with-profile"
                                                                  (str "-user,-dev" extra-profile)
                                                                  "classpath"]
                                                                 [:env env
                                                                  :dir (System/getProperty "user.dir")]]))]
              (when-not (zero? exit)
                (println out)
                (println err)
                (assert false))
              (string/split out #":")))]
    (let [runs (->> [",+self-test"
                     ""]
                    (map run))
          [count-with count-without] (->> runs (map count))]
      (assert (> count-with count-without)
              (pr-str [count-with count-without runs])))))

(defn java-source-paths-test! []
  (info "Running `java-source-paths-test!`")
  (run-repos! classpath-commands-for-java-source-paths-test
              (fn [id classpath]
                (assert (->> (string/split classpath #":")
                             (filter (fn [s]
                                       (let [f (File. s)]
                                         (when (-> f .exists)
                                           (-> f .isDirectory)))))
                             (some (fn [s]
                                     (string/includes? s "/java"))))
                        [id classpath]))))

(defn suite []

  (when-not *assert*
    (throw (ex-info "." {})))

  (sh lein "install" :dir (System/getProperty "user.dir") :env env)

  ;; Pedestal needs separate invocations for `install`, `deps`:
  (let [{:keys [out exit err]} (apply sh (reduce into [[lein "with-profile" "-user"
                                                        "sub" "with-profile" "-user" "install"]
                                                       [:dir (submodule-dir "pedestal")
                                                        :env env]]))]
    (when-not (zero? exit)
      (println out)
      (println err)
      (assert false)))

  (sh "rm" "-rf" (jdk-sources/uncompressed-sources-dir))
  (-> sut/cache-filename File. .delete)

  (run-repos! deps-commands
              (fn [id good-lines]
                (when (#{1} parallelism-factor)
                  ;; This assertion cannot be guaranteed in parallel runs, since different orderings mean work can get "stolen"
                  ;; from one project to another (which is perfectly normal and desirable)
                  (assert (seq good-lines)
                          (format "Finds sources in %s" id)))
                (info (format "Found %s sources in %s."
                              (count good-lines)
                              id))))

  (run-repos! deps-commands
              (fn [id good-lines]
                (assert (empty? good-lines)
                        [id
                         "Caches the findings"
                         (count good-lines)])))

  ;; Run one last time, proving that a given project's cache building is accretive:
  (run-repos! deps-commands
              (fn [id good-lines]
                (run! info good-lines)
                (assert (empty? good-lines)
                        [id
                         "The cache only accretes - other projects' cache building doesn't undo prior work"
                         (count good-lines)])))

  (let [v (-> sut/cache-filename locks/read-file! sut/safe-read-string)]
    (assert (= v
               (-> v sut/deserialize sut/serialize))
            "Roundtrip")
    (assert (= v
               (-> v sut/deserialize sut/serialize sut/deserialize sut/serialize))
            "Longer roundtrip"))

  (java-source-paths-test!)

  (classpath-test!))

(defn -main [& _]

  (Thread/setDefaultUncaughtExceptionHandler
   (reify Thread$UncaughtExceptionHandler
     (uncaughtException [_ thread ex]
       (-> ex .printStackTrace)
       (System/exit 1))))

  (suite)

  (shutdown-agents)

  (System/exit 0))
