(ns integration.cider.enrich-classpath.source-analysis
  (:require
   [cider.enrich-classpath.source-analysis :as sut]
   [clojure.test :refer [are deftest is testing]]))

(deftest bad-source?
  (are [desc input expected] (testing input
                               (cemerick.pomegranate.aether/resolve-dependencies :coordinates [input])
                               (is (= expected
                                      (sut/bad-source? input))
                                   desc)
                               true)
    "A vanilla Java source dep is not a 'bad source'"
    '[com.zaxxer/HikariCP "4.0.3" :classifier "sources"] false

    "A source artifact for a Clojure dep is a bad source, because it doesn't bundle .java files"
    '[org.clojure/tools.namespace "0.3.1" :classifier "sources"] true

    "The source artifact for clojure.core is not a bad source, because it bundles .java files"
    '[org.clojure/clojure "1.10.3" :classifier "sources"] false

    "A source artifact including a factory file is a bad source"
    '[net.sourceforge.nekohtml/nekohtml "1.9.22" :classifier "sources"] true

    "A source artifact including a factory file is a bad source"
    '[ch.qos.logback/logback-classic "1.2.3" :classifier "sources"] true))
