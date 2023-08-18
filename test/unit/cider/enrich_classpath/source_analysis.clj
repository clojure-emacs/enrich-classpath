(ns unit.cider.enrich-classpath.source-analysis
  (:require
   [cider.enrich-classpath.source-analysis :as sut]
   [clojure.test :refer [are deftest is testing]]))

(deftest factory-file-like-re
  (are [input expected] (testing input
                          (is (= expected
                                 (boolean (re-find sut/factory-file-like-re input))))
                          true)
    "java.foo"                                  false
    "java.Foo"                                  true
    "java.foo.bar"                              false
    "java.foo.Bar"                              true
    "foo.java"                                  false
    "foo.html"                                  false
    "Foo.java"                                  false
    "javax.servlet.ServletContainerInitializer" true
    "com.fasterxml.jackson.core.JsonFactory"    true
    "org.eclipse.jetty"                         false
    "org.eclipse.jetty.websocket"               false
    "org.eclipse.jetty.Websocket"               true)

  (doseq [input sut/factory-files]
    (is (re-find sut/factory-file-like-re input))))
