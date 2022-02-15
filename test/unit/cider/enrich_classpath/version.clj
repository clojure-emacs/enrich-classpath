(ns unit.cider.enrich-classpath.version
  (:require
   [cider.enrich-classpath.version :as sut]
   [clojure.test :refer [are deftest is testing]]))

(deftest outdated-data-version?
  (are [input expected] (testing input
                          (is (= expected
                                 (sut/outdated-data-version? input)))
                          true)
    nil                                                true
    {}                                                 true
    {:enrich-classpath/version (dec sut/data-version)} true
    {:enrich-classpath/version sut/data-version}       false))
