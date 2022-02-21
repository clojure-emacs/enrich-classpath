(ns integration-test
  (:refer-clojure :exclude [time])
  (:require
   [cider.enrich-classpath :as sut]
   [cider.enrich-classpath.collections :refer [divide-by]]
   [cider.enrich-classpath.jdk :as jdk]
   [cider.enrich-classpath.jdk-sources :as jdk-sources]
   [cider.enrich-classpath.locks :as locks]
   [cider.enrich-classpath.logging :refer [info]]
   [clojure.java.io :as io]
   [clojure.java.shell :refer [sh]]
   [clojure.string :as string])
  (:import
   (java.io File)
   (java.util.regex Pattern)))

(def ansi-pattern (Pattern/compile "\\e\\[.*?m"))

(defn strip-ansi ^String [s]
  (string/replace s ansi-pattern ""))

(def exercise-shorten? (let [env-var-name "enrich_classpath_ci_shorten"]
                         (-> env-var-name
                             System/getenv
                             (doto (assert (str env-var-name " is unset")))
                             read-string)))

(def slice (let [env-var-name "enrich_classpath_ci_slice"]
             (-> env-var-name
                 System/getenv
                 (doto (assert (str env-var-name " is unset")))
                 read-string
                 dec)))

(def env (-> (into {} (System/getenv))
             (dissoc "CLASSPATH")
             ;; for Lein logging:
             (assoc "DEBUG" "true")
             (assoc "LEIN_JVM_OPTS" "-Dclojure.main.report=stderr")))

(def lein (->> [;; DeLaGuardo/setup-clojure (linux)
                (io/file "/opt" "hostedtoolcache" "Leiningen" "2.8.1" "x64" "bin" "lein")
                (io/file "/opt" "hostedtoolcache" "Leiningen" "2.9.4" "x64" "bin" "lein")
                (io/file "/opt" "hostedtoolcache" "Leiningen" "2.9.4-3-6" "x64" "bin" "lein")
                ;; DeLaGuardo/setup-clojure (macOS)
                (io/file "/Users" "runner" "hostedtoolcache" "Leiningen" "2.8.1" "x64" "bin" "lein")
                (io/file "/Users" "runner" "hostedtoolcache" "Leiningen" "2.9.4" "x64" "bin" "lein")
                (io/file "/Users" "runner" "hostedtoolcache" "Leiningen" "2.9.4-3-6" "x64" "bin" "lein")
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

(def project-version "999.99.9")

(assert (string? project-version))

(when (System/getenv "CI")
  (-> "user.home"
      System/getProperty
      io/file
      (io/file ".lein")
      (doto .mkdirs)
      (io/file "profiles.clj")
      (str)
      (spit (pr-str {:enrich-classpath {:middleware ['cider.enrich-classpath/middleware]}}))))

(defn prelude* [x profile]
  (cond-> [x
           "with-profile" profile

           "update-in"
           ":" "assoc" ":pedantic?" "false"
           "--"

           "update-in"
           ":" "dissoc" ":javac-options"
           "--"

           "update-in"
           ":plugins" "conj" (str "[mx.cider/enrich-classpath \""
                                  project-version
                                  "\"]")
           "--"

           "update-in"
           ":" "assoc" ":enrich-classpath" (format "{:classifiers #{\"sources\"}%s}"
                                                   (if exercise-shorten?
                                                     " :shorten true}"
                                                     "}"))
           "--"]))

(defn prelude [x]
  (into (prelude* x "-user,+test")
        ["update-in"
         ":middleware" "conj" "cider.enrich-classpath/middleware"
         "--"]))

(defn prelude-for-run [x]
  ;; XXX injected profiles don't work
  (prelude* x "-user,+test,+enrich-classpath"))

(def vanilla-lein-deps
  (conj (prelude lein) "deps"))

(def run-command ["run" "-m" "clojure.main" "--" "-e" "(println *clojure-version*)"])

(def vanilla-lein-run
  (into (prelude-for-run lein) run-command))

(defn take-slice [coll]
  (nth (partition-all 5 coll) slice))

(def deps-commands
  (take-slice
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
     "incanter"      (reduce into [] [(prelude lein)
                                      ["sub" "do"]
                                      (prelude "install,")
                                      ["deps"]])
     ;; uses lein-sub:
     "icepick"       (with-meta (reduce into [] [(prelude lein)
                                                 (prelude "sub")
                                                 ["deps"]])
                       ;; Icepick seemingly relies on tools.jar, unavailable in JDK9+
                       {::skip-in-newer-jdks true
                        ::running-needs-shortening true})
     ;; uses lein-sub:
     "crux"          (reduce into [] [(prelude lein)
                                      (prelude "sub")
                                      ["deps"]])
     ;; uses lein-sub:
     "pedestal"      (reduce into [] [(prelude lein)
                                      (prelude "sub")
                                      ["deps"]])
     ;; uses lein-monolith:
     "sparkplug"     (with-meta (reduce into [] [(prelude lein)
                                                 ["monolith"]
                                                 (prelude "each")
                                                 ["do"
                                                  "clean,"
                                                  "install,"
                                                  "deps"]])
                       ;; something fipp-related (unrelated to our vendored version):
                       {::skip-in-newer-jdks true})})))

(def run-commands
  (take-slice
   (sort ;; ensure stable tests
    {"aleph"         vanilla-lein-run
     "amazonica"     vanilla-lein-run
     "carmine"       vanilla-lein-run
     "cassaforte"    vanilla-lein-run
     "cider-nrepl"   vanilla-lein-run
     "elastisch"     vanilla-lein-run
     "http-kit"      vanilla-lein-run
     "jackdaw"       vanilla-lein-run
     "langohr"       vanilla-lein-run
     "machine_head"  vanilla-lein-run
     "metabase"      vanilla-lein-run
     "monger"        vanilla-lein-run
     "pallet"        vanilla-lein-run
     "quartzite"     vanilla-lein-run
     "riemann"       vanilla-lein-run
     "welle"         vanilla-lein-run
     ;; uses various plugins:
     "schema"        (with-meta vanilla-lein-run
                       ;; something core.rrb-vector related
                       {::skip-in-newer-jdks true})
     ;; uses lein-parent:
     "trapperkeeper" vanilla-lein-run
     ;; uses lein-parent:
     "jepsen/jepsen" vanilla-lein-run
     ;; uses lein-tools-deps:
     "overtone"      vanilla-lein-run
     ;; uses lein-sub, lein-modules:
     "incanter"      (reduce into [] [(prelude lein)
                                      ["sub" "do"]
                                      (prelude-for-run "install,")
                                      run-command])
     ;; uses lein-sub:
     "icepick"       (with-meta (reduce into [] [(prelude lein)
                                                 (prelude-for-run "sub")
                                                 run-command])
                       ;; Icepick seemingly relies on tools.jar, unavailable in JDK9+
                       {::skip-in-newer-jdks true
                        ::running-needs-shortening true})
     ;; uses lein-sub:
     "crux"          (reduce into [] [(prelude lein)
                                      (prelude-for-run "sub")
                                      run-command])
     ;; uses lein-sub:
     "pedestal"      (reduce into [] [(prelude lein)
                                      (prelude-for-run "sub")
                                      run-command])
     ;; uses lein-monolith:
     "sparkplug"     (with-meta (reduce into [] [(prelude lein)
                                                 ["monolith"]
                                                 (prelude-for-run "each")
                                                 ["do"
                                                  "clean,"
                                                  "install,"]
                                                 run-command])
                       ;; something fipp-related (unrelated to our vendored version):
                       {::skip-in-newer-jdks true})})))

(def smoke-commands
  (into {}
        (comp (remove (comp (cond-> #{"crux" ;; hangs
                                      "sparkplug" ;; hangs
                                      "incanter" ;; hangs
                                      "icepick" ;; doesn't show a real issue but one caused by the ci setup itself
                                      "metabase" ;; javac
                                      }
                              ;; overtone uses a deprecated JVM option
                              (not (jdk/jdk8?)) (conj "overtone"))
                            key)))
        run-commands))

(def classpath-commands
  (into {}
        (map (fn [[id command]]
               {:pre [(-> command last #{"deps"})]}
               (let [idx (-> command count dec)]
                 [id, (with-meta (-> command
                                     (subvec 0 idx)
                                     (conj "classpath"))
                        (meta command))])))
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
                            (let [checking-deps? (= commands-corpus deps-commands)
                                  smoke-testing? (= commands-corpus smoke-commands)
                                  checking-classpath? ((hash-set classpath-commands
                                                                 classpath-commands-for-java-source-paths-test)
                                                       commands-corpus)]
                              (if (or (and (not (-> "java.version" System/getProperty (string/starts-with? "1.8")))
                                           (-> command meta ::skip-in-newer-jdks))
                                      (and smoke-testing?
                                           (not exercise-shorten?)
                                           (-> command meta ::running-needs-shortening)))
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
                                  (let [lines (cond->> out
                                                true string/split-lines

                                                checking-deps?
                                                (filter (fn [s]
                                                          (string/includes? s "cider.enrich-classpath")))

                                                (or smoke-testing?
                                                    checking-classpath?)
                                                (take-last 1)

                                                (and (not checking-deps?)
                                                     (not checking-classpath?)
                                                     (not smoke-testing?))
                                                (assert false))
                                        good (cond->> lines
                                               checking-deps?
                                               (filter (fn [s]
                                                         (string/includes? s "/found")))

                                               (or smoke-testing?
                                                   checking-classpath?)
                                               first

                                               (and (not checking-deps?)
                                                    (not smoke-testing?)
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
                                    (assert (-> timing count pos?)
                                            (pr-str id))
                                    (f id good)
                                    (info (str id " - " (first timing)))
                                    id)))))))))

       (apply concat)

       doall))

(defn classpath-test! []
  (info "Running `classpath-test!`")
  (letfn [(run [extra-profile]
            {:post [(-> % count pos?)]}
            (let [{:keys [out err exit]} (apply sh (reduce into [] [[lein
                                                                     "with-profile"
                                                                     (str "-user,-dev" extra-profile)
                                                                     "classpath"]
                                                                    [:env (assoc env
                                                                                 "no_eval_in_leiningen" "true")
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

(defn smoke-test! []
  (info "Running `smoke-test!`")
  ;; one needs to set prep-tasks to [] and run javac without enrich beforehand
  (run-repos! smoke-commands
              (fn [id output]
                (assert (or (string/starts-with? output "{:major 1")
                            (-> output strip-ansi (string/includes? "SUCCESS: Applied with-profile")))
                        (pr-str [id output])))))

(defn metadata-test! []
  (info "Running `metadata-test!`")
  (let [dep-count (atom 0)
        vals (->> sut/cache-filename
                  locks/read-file!
                  sut/safe-read-string
                  sut/deserialize
                  vals
                  (keep seq))]
    (assert (< 300 (count vals)))
    (doseq [v vals
            [_ deps] v
            dep deps]
      (assert (-> dep meta :file not-empty string?)
              (pr-str {:msg "Every dependency must have `:file` metadata, so that the `:shorten` option can work properly."
                       :dep dep
                       :deps deps
                       :v v}))
      (swap! dep-count inc))
    (assert (< 900 @dep-count))))

(defn suite []

  (when-not *assert*
    (throw (ex-info "." {})))

  (let [{:keys [out err exit]} (sh "make" "install"
                                   :dir (System/getProperty "user.dir") :env
                                   (assoc env "PROJECT_VERSION" project-version))]
    (when-not (zero? exit)
      (println out)
      (println err)
      (assert false)))

  ;; Pedestal needs separate invocations for `install`, `deps`:
  (let [{:keys [out exit err]} (apply sh (reduce into [] [[lein "with-profile" "-user"
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

  (smoke-test!)

  (when-not exercise-shorten?
    (classpath-test!))

  (metadata-test!))

(defn -main [& _]

  (Thread/setDefaultUncaughtExceptionHandler
   (reify Thread$UncaughtExceptionHandler
     (uncaughtException [_ thread ex]
       (-> ex .printStackTrace)
       (System/exit 1))))

  (suite)

  (shutdown-agents)

  (System/exit 0))
