(ns unit.cider.enrich-classpath.plugin-v2
  (:require
   [cider.enrich-classpath.plugin-v2 :as sut]
   [clojure.string :as string]
   [clojure.test :refer [are deftest is testing use-fixtures]]))

(deftest build-init-form
  (are [input expected] (testing input
                          (is (= expected
                                 (sut/build-init-form input)))
                          true)
    {}
    " "

    {:repl-options {:init 2}}
    " --eval \"(try 2 (catch java.lang.Throwable e (.printStackTrace e)))\" "

    {:repl-options {:init-ns 'foo}}
    " --eval \"(try (clojure.core/doto (quote foo) clojure.core/require clojure.core/in-ns) (catch java.lang.Throwable e (.printStackTrace e)))\" "

    {:repl-options {:init 2 :init-ns 'foo}}
    " --eval \"(try (clojure.core/doto (quote foo) clojure.core/require clojure.core/in-ns) 2 (catch java.lang.Throwable e (.printStackTrace e)))\" "

    {:global-vars {'*warn-on-reflection* true}}
    " --eval \"(try (set! *warn-on-reflection* true) (catch java.lang.Throwable e (.printStackTrace e)))\" "

    {:repl-options {:init 2 :init-ns 'foo} :global-vars {'*warn-on-reflection* true
                                                         'bar/*quux* 42}}
    " --eval \"(try (set! *warn-on-reflection* true) (clojure.core/doto (quote foo) clojure.core/require clojure.core/in-ns) (set! bar/*quux* 42) 2 (catch java.lang.Throwable e (.printStackTrace e)))\" "))
