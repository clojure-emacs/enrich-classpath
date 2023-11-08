(ns integration.cider.enrich-classpath.clojure
  (:require
   [cider.enrich-classpath]
   [cider.enrich-classpath.clojure :as sut]
   [cider.enrich-classpath.jdk :as jdk]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.test :refer [deftest is testing use-fixtures]])
  (:import
   (java.io File)))

(use-fixtures :each (fn [t]
                      (when-not (System/getenv "CI")
                        (-> cider.enrich-classpath/cache-filename File. .delete))
                      (t)))

(deftest works
  (testing "`shorten?` set to `false`"
    (testing "Returns a valid command with an -Scp specifying an enriched classpath, carefully sorted, and honoring aliases"
      (let [actual (sut/impl "clojure" "sample.deps.edn"
                             (System/getProperty "user.dir")
                             ["-A:some-alias"]
                             false)]
        (testing actual
          (is (-> actual (.contains "\"-A:some-alias\""))
              "Applies `pr-str` over args")
          (is (-> actual (.contains "src:test:other:the-extra-path:resource:resources")))
          (is (-> actual (.contains "the-extra-path")))
          (is (-> actual (.contains "refactor-nrepl")))
          (is (-> actual (.contains "-Scp")))
          (is (-> actual (.contains "src.zip")))
          (is (-> actual (.contains "-sources.jar")))
          (is (-> actual (.contains "grpc-protobuf-1.14.0-sources.jar"))
              "Includes a source from a dependency fetched through a `:local/root` reference")
          (is (-> actual (.contains "-javadoc.jar")))
          (when (re-find #"^1\.8\." (System/getProperty "java.version"))
            (is (-> actual (.contains "unzipped-jdk-sources"))))))))

  (testing "`shorten?` set to `true`"
    (testing "Returns a valid command with an -Scp specifying an enriched classpath, carefully sorted, and honoring aliases"
      (let [actual (sut/impl "clojure" "sample.deps.edn"
                             (System/getProperty "user.dir")
                             ["-A:some-alias"]
                             true)]
        (testing actual
          (is (-> actual (.contains "src:test:other:the-extra-path:resource:resources")))
          (is (-> actual (.contains "the-extra-path")))
          (is (-> actual (.contains "refactor-nrepl")))
          (is (-> actual (.contains "-Scp")))
          (is (-> actual (.contains "src.zip")))
          (is (not (-> actual (.contains "-sources.jar"))))
          (is (not (-> actual (.contains "-javadoc.jar"))))
          (is (-> actual (.contains (str "mx.cider/enrich-classpath/" (jdk/digits-str)))))
          (when (re-find #"^1\.8\." (System/getProperty "java.version"))
            (is (-> actual (.contains "unzipped-jdk-sources"))))))))

  (is (any? (sut/impl "clojure"
                      "deps.edn"
                      (str (io/file (System/getProperty "user.dir") "test-resources" "extra-project"))
                      []
                      false))
      "Can run a once-problematic project")

  (is (string/includes? (sut/impl "clojure"
                                  "deps.edn"
                                  (str (io/file (System/getProperty "user.dir") "test-resources" "git-project"))
                                  []
                                  false)
                        "gitlibs/libs/clj-hcl/clj-hcl/571c4cf715e34fad9c8f09c7b5319f8d4c395d90/src")
      "Includes gitlibs")

  (let [cp (sut/impl "clojure"
                     "deps.edn"
                     (str (io/file (System/getProperty "user.dir")
                                   "test-resources"
                                   "local-root-dotdot"
                                   "grandchild"))
                     []
                     false)]
    (is (string/includes? cp
                          "a-unique-source-path")
        "Includes `:local/root`s expressed as `..` or `../<dir>` ")
    (is (string/includes? cp
                          "org/clojars/brenton/google-diff-match-patch/0.1/google-diff-match-patch-0.1.jar")
        "Includes `:local/root`s expressed as `..` or `../<dir>` "))

  (is (any? (sut/impl "clojure"
                      "deps.edn"
                      (str (io/file (System/getProperty "user.dir") "test-resources" "another-project"))
                      []
                      false))
      "Can run a once-problematic project")

  (let [v (sut/impl "clojure"
                    "deps.edn"
                    (System/getProperty "user.dir")
                    ["-Sdeps"
                     (pr-str '{:deps {nrepl/nrepl {:mvn/version "1.0.0"} cider/cider-nrepl {:mvn/version "0.36.0"} refactor-nrepl/refactor-nrepl {:mvn/version "3.9.0"}} :aliases {:cider/nrepl {:main-opts ["-m" "nrepl.cmdline" "--middleware" "[refactor-nrepl.middleware/wrap-refactor,cider.nrepl/cider-middleware]"]}}})
                     "-M:dev:test:cider/nrepl"]
                    false)
        opens "-J--add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED"
        opens2 "-J--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED"]
    (testing v
      (is (string/starts-with? v
                               "clojure -Sforce -Srepro -J-XX:-OmitStackTraceInFastThrow -J-Dclojure.main.report=stderr -Scp")
          "Looks like a well-formed `clojure` invocation")
      (is (string/ends-with? v
                             "-m nrepl.cmdline --middleware [refactor-nrepl.middleware/wrap-refactor,cider.nrepl/cider-middleware]")
          "Adds cider-nrepl's --main program based on the -Sdeps and -M args")
      (is (string/includes? v
                            ".m2/repository/cider/cider-nrepl/0.36.0/cider-nrepl-0.36.0.jar")
          "Adds the cider-nrepl dep and program based on the -Sdeps and -M args")
      (if (jdk/jdk8?)
        (is (not (string/includes? v opens)))
        (is (string/includes? v opens)))
      (if (jdk/jdk8?)
        (is (not (string/includes? v opens2)))
        (is (string/includes? v opens2))))))

(deftest classpath-overrides
  (let [cp (sut/impl "clojure"
                     "deps.edn"
                     (str (io/file (System/getProperty "user.dir") "test-resources" "flowstorm"))
                     ["-A:flowstorm"]
                     false)]
    (is (string/includes? cp "com/github/jpmonettas/clojure/1.12.0-alpha4_3/clojure-1.12.0-alpha4_3.jar")
        "Honors `:classpath-overrides`")
    (is (not (string/includes? cp "org/clojure/clojure"))
        "Honors `:classpath-overrides`")))

(deftest bouncy-repro
  (testing "A problematic real-world case doesn't throw exceptions"
    (let [actual (sut/impl "clojure"
                           "deps.edn"
                           (str (io/file (System/getProperty "user.dir") "test-resources" "bouncy"))
                           ["-A:test"]
                           false)]
      (is (-> actual (.contains "-sources.jar")))
      (is (-> actual (.contains "-javadoc.jar")))
      (is (-> actual (.contains "src.zip")))
      (if (re-find #"^1\.8\." (System/getProperty "java.version"))
        (is (-> actual (.contains "unzipped-jdk-sources")))
        (is (-> actual (.contains "-J--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED")))))))

(deftest eval-option
  (let [cp (sut/impl "clojure"
                     "deps.edn"
                     (str (io/file (System/getProperty "user.dir") "test-resources" "eval"))
                     ["-A:eval"]
                     false)]
    (is (string/includes? cp "-e \"(println \\\"foo\\\")\"")
        "Escapes the -e value")))

(when-not (jdk/jdk8?)
  (deftest jvm-opts
    (testing "https://github.com/clojure-emacs/enrich-classpath/issues/56"
      (let [cp (sut/impl "clojure"
                         "deps.edn"
                         (str (io/file (System/getProperty "user.dir") "test-resources" "jvm-opts"))
                         ["-M:foo"]
                         false)]
        (is (not (string/includes? cp "-M"))
            "Removes -M")

        (assert (pos? (long (string/index-of cp "-J--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED"))))
        (is (= (string/index-of cp "-J--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED")
               (string/last-index-of cp "-J--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED"))
            "Doesn't add this flag twice")

        (assert (pos? (long (string/index-of cp "-J--add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED"))))
        (is (= (string/index-of cp "-J--add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED")
               (string/last-index-of cp "-J--add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED"))
            "Doesn't add this flag twice")

        (is (string/ends-with? cp (str "-J--add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED "
                                       "-J--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED "
                                       "-J-XstartOnFirstThread"))
            "Adds -J-XstartOnFirstThread as derived from -M, using the same format as other -J options")))))
