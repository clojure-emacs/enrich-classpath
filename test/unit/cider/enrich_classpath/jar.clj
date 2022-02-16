(ns unit.cider.enrich-classpath.jar
  (:require
   [cider.enrich-classpath.jar :as sut]
   [clojure.string :as string]
   [clojure.test :refer [are deftest is testing]]))

(deftest wrap72
  (testing "Wraps text every 72 characters, inserting one space of padding for every inserted line break"
    (are [input expected-output expected-output-jdk8 expected-newline-count]
        (testing input
          (if (re-find #"^1\.8\." (System/getProperty "java.version"))
            (is (= expected-output-jdk8
                   (sut/wrap72 input)))
            (is (= expected-output
                   (sut/wrap72 input))))
          (is (<= (->> input
                       sut/wrap72
                       string/split-lines
                       (map count)
                       (apply max)
                       (long))
                  72))
          (is (= expected-newline-count
                 (->> input
                      sut/wrap72
                      (re-seq #"\r\n ")
                      (count))))
          (is (= input
                 (-> input
                     sut/wrap72
                     (string/split #"\r\n ")
                     (string/join)))
              "The output has essentially the same info than the input, with no info being lost or added")
          true)
      "a"
      "a"
      "a"
      0

      "/Users/vemv/.m2/repository/rewrite-clj/rewrite-clj/1.0.594-alpha/rewrite-clj-1.0.594-alpha.jar"
      "/Users/vemv/.m2/repository/rewrite-clj/rewrite-clj/1.0.594-alpha/rewrite\r\n -clj-1.0.594-alpha.jar"
      "/Users/vemv/.m2/repository/rewrite-clj/rewrite-clj/1.0.594-alpha/rewri\r\n te-clj-1.0.594-alpha.jar"
      1

      "/Users/vemv/.m2/repository/rewrite-clj/rewrite-clj/1.0.594-alpha/rewrite-clj-1.0.594-alpha.jar /Users/vemv/.m2/repository/org/checkerframework/checker-qual/2.0.0/checker-qual-2.0.0.jar"
      "/Users/vemv/.m2/repository/rewrite-clj/rewrite-clj/1.0.594-alpha/rewrite\r\n -clj-1.0.594-alpha.jar /Users/vemv/.m2/repository/org/checkerframework/\r\n checker-qual/2.0.0/checker-qual-2.0.0.jar"
      "/Users/vemv/.m2/repository/rewrite-clj/rewrite-clj/1.0.594-alpha/rewri\r\n te-clj-1.0.594-alpha.jar /Users/vemv/.m2/repository/org/checkerframew\r\n ork/checker-qual/2.0.0/checker-qual-2.0.0.jar"
      2)))
