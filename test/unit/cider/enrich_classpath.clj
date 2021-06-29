(ns unit.cider.enrich-classpath
  (:require
   [cider.enrich-classpath :as sut]
   [clojure.test :refer [are deftest is testing]]))

(deftest matches-version?
  (let [a '[foo "2.1.2"]
        b '[foo "2.1.3"]]
    (are [deps artifact expected] (testing [deps artifact]
                                    (is (= expected
                                           (sut/matches-version? deps artifact)))
                                    true)
      []    a true
      [a]   a true
      [b]   a false
      [a b] a true)))

(deftest choose-one-artifact
  (let [a-orig '[foo "2.1.2"]
        b-orig '[foo "2.1.3"]
        a-source  '[foo "2.1.2" :classifier "sources"]
        b-source  '[foo "2.1.3" :classifier "sources"]]
    (are [desc deps managed-dependencies equivalent-deps expected] (testing [deps managed-dependencies equivalent-deps]
                                                                     (is (= expected
                                                                            (sut/choose-one-artifact deps
                                                                                                     managed-dependencies
                                                                                                     equivalent-deps))
                                                                         desc)
                                                                     true)
      "Basic"
      []       []       [a-source]          a-source

      "Basic - chooses the most recent dep (1/2)"
      []       []       [a-source b-source] b-source

      "Basic - chooses the most recent dep (2/2)"
      []       []       [b-source a-source] b-source

      "Can choose from `deps`"
      [a-orig] []       [a-source]          a-source

      "Can choose from `deps`"
      [a-orig] []       [a-source b-source] a-source

      "Can choose from `deps`"
      [b-orig] []       [a-source b-source] b-source

      "Does not choose from `deps` a non-existing source"
      [a-orig] []       [b-source]          b-source

      "Can choose from `managed-deps`"
      []       [a-orig] [a-source]          a-source

      "Can choose from `managed-deps`"
      []       [a-orig] [a-source b-source] a-source

      "Can choose from `managed-deps`"
      []       [b-orig] [a-source b-source] b-source

      "Does not choose from `managed-deps` a non-existing source"
      []       [a-orig] [b-source]          b-source

      "Favors `deps` over `managed-dependencies` even if the latter indicates a more recent version"
      [a-orig] [b-orig] [a-source b-source] a-source)))
