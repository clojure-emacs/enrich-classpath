(ns unit.cider.enrich-classpath.collections
  (:require
   [cider.enrich-classpath.collections :as sut]
   [clojure.test :refer [are deftest is testing]]))

(deftest index
  (are [coll item expected] (testing [coll item]
                              (is (= expected
                                     (sut/index coll item)))
                              true)
    [:a]    :a 0
    [:_ :a] :a 1))

(deftest normalize-exclusions
  (are [input expected] (testing input
                          (is (= expected
                                 (sut/normalize-exclusions input)))
                          true)
    []                                 []
    '[org.clojure/clojure]             '[[org.clojure/clojure]]
    '[[org.clojure/clojure]]           '[[org.clojure/clojure]]
    '[[foo] org.clojure/clojure [bar]] '[[foo] [org.clojure/clojure] [bar]]))

(deftest safe-sort
  (testing "Normalizes :exclusions"
    (are [input expected] (testing input
                            (is (= expected
                                   (sut/safe-sort input)))
                            true)
      '[[[[org.clojure/tools.nrepl "0.2.12" :exclusions [org.clojure/clojure]]] [[[org.clojure/tools.nrepl "0.2.12" :exclusions [[org.clojure/clojure]]] nil]]]
        [[[org.clojure/tools.nrepl "0.2.12" :exclusions [[org.clojure/clojure]]]] [[[org.clojure/tools.nrepl "0.2.12" :exclusions [[org.clojure/clojure]]] nil]]]]
      '([[[org.clojure/tools.nrepl "0.2.12" :exclusions [org.clojure/clojure]]] [[[org.clojure/tools.nrepl "0.2.12" :exclusions [[org.clojure/clojure]]] nil]]]
        [[[org.clojure/tools.nrepl "0.2.12" :exclusions [[org.clojure/clojure]]]] [[[org.clojure/tools.nrepl "0.2.12" :exclusions [[org.clojure/clojure]]] nil]]])

      '[[[[clojure-complete "0.2.4" :exclusions [[org.clojure/clojure]]]] [[[clojure-complete "0.2.4" :exclusions [[org.clojure/clojure]]] nil]]]
        [[[clojure-complete "0.2.4" :exclusions [org.clojure/clojure]]] [[[clojure-complete "0.2.4" :exclusions [[org.clojure/clojure]]] nil]]]]
      '([[[clojure-complete "0.2.4" :exclusions [[org.clojure/clojure]]]] [[[clojure-complete "0.2.4" :exclusions [[org.clojure/clojure]]] nil]]]
        [[[clojure-complete "0.2.4" :exclusions [org.clojure/clojure]]] [[[clojure-complete "0.2.4" :exclusions [[org.clojure/clojure]]] nil]]]))))

(deftest add-exclusions-if-classified
  (are [input expected] (testing input
                          (is (= expected
                                 (sut/add-exclusions-if-classified input)))
                          true)
    []                                                []
    '[foo]                                            '[foo]
    '[foo nil]                                        '[foo nil]
    '[foo "2"]                                        '[foo "2"]
    '[foo "2" :exclusions [a]]                        '[foo "2" :exclusions [a]]
    '[foo "2" :classifier "sources"]                  '[foo "2" :classifier "sources" :exclusions [[*]]]
    '[foo "2" :exclusions ['a] :classifier "sources"] '[foo "2" :exclusions [[*]] :classifier "sources"]))
