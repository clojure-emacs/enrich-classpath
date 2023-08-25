(ns integration.cider.enrich-classpath.clojure
  (:require
   [cider.enrich-classpath.clojure :as sut]
   [cider.enrich-classpath.jdk :as jdk]
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
            (is (-> actual (.contains "unzipped-jdk-sources")))))))))
