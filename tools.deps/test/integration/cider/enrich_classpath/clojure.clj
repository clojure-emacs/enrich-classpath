(ns integration.cider.enrich-classpath.clojure
  (:require
   [cider.enrich-classpath.clojure :as sut]
   [clojure.test :refer [deftest is testing]]))

(deftest works
  (testing "Returns a valid command with an -Scp specifying an enriched classpath, carefully sorted, and honoring aliases"
    (let [actual (sut/impl "clojure" "sample.deps.edn"
                           (System/getProperty "user.dir")
                           ["-Asome-alias"])]
      (testing actual
        (is (-> actual (.contains "src:test:other:the-extra-path:resource:resources")))
        (is (-> actual (.contains "the-extra-path")))
        (is (-> actual (.contains "refactor-nrepl")))
        (is (-> actual (.contains "-Scp")))
        (is (-> actual (.contains "src.zip")))
        (is (-> actual (.contains "-sources.jar")))
        (is (-> actual (.contains "-javadoc.jar")))
        (when (re-find #"^1\.8\." (System/getProperty "java.version"))
          (is (-> actual (.contains "unzipped-jdk-sources"))))))))
