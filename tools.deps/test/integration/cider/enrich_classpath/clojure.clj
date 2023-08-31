(ns integration.cider.enrich-classpath.clojure
  (:require
   [cider.enrich-classpath.clojure :as sut]
   [cider.enrich-classpath.jdk :as jdk]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.test :refer [deftest is testing]]))

(deftest works
  (testing "`shorten?` set to `false`"
    (testing "Returns a valid command with an -Scp specifying an enriched classpath, carefully sorted, and honoring aliases"
      (let [actual (sut/impl "clojure" "sample.deps.edn"
                             (System/getProperty "user.dir")
                             ["-Asome-alias"]
                             false)]
        (testing actual
          (is (-> actual (.contains "\"-Asome-alias\""))
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
                             ["-Asome-alias"]
                             true)]
        (testing actual
          (is (-> actual (.contains "src:test:other:the-extra-path:resource:resources")))
          (is (-> actual (.contains "the-extra-path")))
          (is (-> actual (.contains "refactor-nrepl")))
          (is (-> actual (.contains "-Scp")))
          (is (-> actual (.contains "src.zip")))
          (is (not (-> actual (.contains "-sources.jar"))))
          (is (not (-> actual (.contains "-javadoc.jar"))))
          (is  (-> actual (.contains (str ".mx.cider/enrich-classpath/" (jdk/digits-str)))))
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
                    false)]
    (testing v
      (is (string/starts-with? v
                               "clojure -Sforce -Srepro -J-XX:-OmitStackTraceInFastThrow -J-Dclojure.main.report=stderr -Scp")
          "Looks like a well-formed `clojure` invocation")
      (is (string/ends-with? v
                             "-m nrepl.cmdline --middleware [refactor-nrepl.middleware/wrap-refactor,cider.nrepl/cider-middleware]")
          "Adds cider-nrepl's --main program based on the -Sdeps and -M args")
      (is (string/includes? v
                            ".m2/repository/cider/cider-nrepl/0.36.0/cider-nrepl-0.36.0.jar")
          "Adds the cider-nrepl dep and program based on the -Sdeps and -M args"))))
