(ns integration.cider.enrich-classpath.plugin-v2
  (:require
   [cider.enrich-classpath.plugin-v2 :as sut]
   [clojure.string :as string]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :each (fn [t]
                      (with-redefs [sut/wrap-try (fn [_ x _]
                                                   x)]
                        (t))))

(def classpath-starts-with
  "~/enrich-classpath/lein-plugin/test:~/enrich-classpath/lein-plugin/src:~/enrich-classpath/lein-plugin/resources:~/enrich-classpath/lein-plugin/the-compile-path:~/.m2/repository/clj-time/clj-time/0.11.0/clj-time-0.11.0.jar:~/.m2/repository/org/slf4j/jul-to-slf4j/1.7.20/jul-to-slf4j-1.7.20.jar:~/.m2/repository/com/typesafe/config/1.2.0/config-1.2.0.jar:~/.m2/repository/org/clojure/java.classpath/0.2.2/java.classpath-0.2.2.jar:~/.m2/repository/puppetlabs/typesafe-config/0.1.5/typesafe-config-0.1.5.jar:~/.m2/repository/puppetlabs/trapperkeeper/3.0.0/trapperkeeper-3.0.0.jar:~/.m2/repository/org/clojure/tools.logging/0.4.0/tools.logging-0.4.0.jar:~/.m2/repository/tigris/tigris/0.1.1/tigris-0.1.1.jar:~/.m2/repository/org/eclipse/jetty/jetty-http/9.4.28.v20200408/jetty-http-9.4.28.v20200408.jar:~/.m2/repository/org/eclipse/jetty/jetty-io/9.4.28.v20200408/jetty-io-9.4.28.v20200408.jar:~/.m2/repository/org/tcrawley/dynapath/0.2.5/dynapath-0.2.5.jar:~/.m2/repository/org/codehaus/janino/janino/3.0.8/janino-3.0.8.jar:~/.m2/repository/ring/ring-servlet/1.5.0/ring-servlet-1.5.0.jar:~/.m2/repository/org/clojure/tools.macro/0.1.5/tools.macro-0.1.5.jar:~/.m2/repository/clj-commons/fs/1.5.1/fs-1.5.1.jar:~/.m2/repository/beckon/beckon/0.1.1/beckon-0.1.1.jar:~/.m2/repository/digest/digest/1.4.3/digest-1.4.3.jar:~/.m2/repository/org/codehaus/janino/commons-compiler/3.0.8/commons-compiler-3.0.8.jar:~/.m2/repository/org/slf4j/slf4j-api/1.7.25/slf4j-api-1.7.25.jar:~/.m2/repository/ring/ring-codec/1.0.0/ring-codec-1.0.0.jar:~/.m2/repository/org/eclipse/jetty/websocket/websocket-common/9.4.28.v20200408/websocket-common-9.4.28.v20200408.jar:~/.m2/repository/org/eclipse/jetty/jetty-xml/9.4.28.v20200408/jetty-xml-9.4.28.v20200408.jar:~/.m2/repository/org/clojure/tools.analyzer/0.6.9/tools.analyzer-0.6.9.jar:~/.m2/repository/prismatic/schema/1.1.9/schema-1.1.9.jar:~/.m2/repository/com/fasterxml/jackson/dataformat/jackson-dataformat-smile/2.9.0/jackson-dataformat-smile-2.9.0.jar:~/.m2/repository/puppetlabs/trapperkeeper-webserver-jetty9/4.1.0/trapperkeeper-webserver-jetty9-4.1.0.jar:~/.m2/repository/org/gnu/gettext/libintl/0.18.3/libintl-0.18.3.jar:~/.m2/repository/org/eclipse/jetty/jetty-server/9.4.28.v20200408/jetty-server-9.4.28.v20200408.jar:~/.m2/repository/org/eclipse/jetty/jetty-util/9.4.28.v20200408/jetty-util-9.4.28.v20200408.jar:~/.m2/repository/org/clojure/tools.reader/1.0.0-beta4/tools.reader-1.0.0-beta4.jar:~/.m2/repository/ch/qos/logback/logback-classic/1.2.3/logback-classic-1.2.3.jar:~/.m2/repository/prismatic/plumbing/0.4.2/plumbing-0.4.2.jar:~/.m2/repository/org/eclipse/jetty/jetty-continuation/9.4.28.v20200408/jetty-continuation-9.4.28.v20200408.jar:~/.m2/repository/org/eclipse/jetty/jetty-security/9.4.28.v20200408/jetty-security-9.4.28.v20200408.jar:~/.m2/repository/org/clojure/core.memoize/0.5.9/core.memoize-0.5.9.jar:~/.m2/repository/org/clojure/tools.cli/0.3.6/tools.cli-0.3.6.jar:~/.m2/repository/org/flatland/ordered/1.5.7/ordered-1.5.7.jar:~/.m2/repository/org/flatland/useful/0.11.6/useful-0.11.6.jar:~/.m2/repository/org/eclipse/jetty/jetty-jmx/9.4.28.v20200408/jetty-jmx-9.4.28.v20200408.jar:~/.m2/repository/org/eclipse/jetty/websocket/websocket-servlet/9.4.28.v20200408/websocket-servlet-9.4.28.v20200408.jar:~/.m2/repository/puppetlabs/i18n/0.8.0/i18n-0.8.0.jar:~/.m2/repository/org/eclipse/jetty/jetty-servlet/9.4.28.v20200408/jetty-servlet-9.4.28.v20200408.jar:~/.m2/repository/cheshire/cheshire/5.8.0/cheshire-5.8.0.jar:~/.m2/repository/de/kotka/lazymap/3.1.0/lazymap-3.1.0.jar:~/.m2/repository/org/yaml/snakeyaml/1.24/snakeyaml-1.24.jar:~/.m2/repository/org/ow2/asm/asm-all/4.2/asm-all-4.2.jar:~/.m2/repository/org/clojure/data.priority-map/0.0.7/data.priority-map-0.0.7.jar:~/.m2/repository/cpath-clj/cpath-clj/0.1.2/cpath-clj-0.1.2.jar:~/.m2/repository/org/eclipse/jetty/jetty-client/9.4.28.v20200408/jetty-client-9.4.28.v20200408.jar:~/.m2/repository/puppetlabs/kitchensink/3.0.0/kitchensink-3.0.0.jar:~/.m2/repository/com/fasterxml/jackson/core/jackson-core/2.9.0/jackson-core-2.9.0.jar:~/.m2/repository/javax/servlet/javax.servlet-api/3.1.0/javax.servlet-api-3.1.0.jar:~/.m2/repository/org/clojure/clojure/1.10.1/clojure-1.10.1.jar:~/.m2/repository/clj-commons/clj-yaml/0.7.0/clj-yaml-0.7.0.jar:~/.m2/repository/com/fasterxml/jackson/dataformat/jackson-dataformat-cbor/2.9.0/jackson-dataformat-cbor-2.9.0.jar:~/.m2/repository/org/ini4j/ini4j/0.5.2/ini4j-0.5.2.jar:~/.m2/repository/ch/qos/logback/logback-access/1.2.3/logback-access-1.2.3.jar:~/.m2/repository/nrepl/nrepl/1.0.0/nrepl-1.0.0.jar:~/.m2/repository/org/slf4j/log4j-over-slf4j/1.7.20/log4j-over-slf4j-1.7.20.jar:~/.m2/repository/org/eclipse/jetty/websocket/websocket-api/9.4.28.v20200408/websocket-api-9.4.28.v20200408.jar:~/.m2/repository/org/clojure/core.specs.alpha/0.2.44/core.specs.alpha-0.2.44.jar:~/.m2/repository/slingshot/slingshot/0.12.2/slingshot-0.12.2.jar:~/.m2/repository/org/apache/commons/commons-compress/1.18/commons-compress-1.18.jar:~/.m2/repository/org/eclipse/jetty/websocket/websocket-client/9.4.28.v20200408/websocket-client-9.4.28.v20200408.jar:~/.m2/repository/org/eclipse/jetty/jetty-webapp/9.4.28.v20200408/jetty-webapp-9.4.28.v20200408.jar:~/.m2/repository/puppetlabs/ssl-utils/3.0.4/ssl-utils-3.0.4.jar:~/.m2/repository/org/clojure/core.cache/0.6.5/core.cache-0.6.5.jar:~/.m2/repository/ch/qos/logback/logback-core/1.2.3/logback-core-1.2.3.jar:~/.m2/repository/org/clojure/java.jmx/0.3.4/java.jmx-0.3.4.jar:~/.m2/repository/org/clojure/core.async/0.4.490/core.async-0.4.490.jar:~/.m2/repository/org/eclipse/jetty/jetty-proxy/9.4.28.v20200408/jetty-proxy-9.4.28.v20200408.jar:~/.m2/repository/commons-codec/commons-codec/1.10/commons-codec-1.10.jar:~/.m2/repository/joda-time/joda-time/2.8.2/joda-time-2.8.2.jar:~/.m2/repository/org/clojure/spec.alpha/0.2.176/spec.alpha-0.2.176.jar:~/.m2/repository/puppetlabs/trapperkeeper-filesystem-watcher/1.2.0/trapperkeeper-filesystem-watcher-1.2.0.jar:~/.m2/repository/org/eclipse/jetty/websocket/websocket-server/9.4.28.v20200408/websocket-server-9.4.28.v20200408.jar:~/.m2/repository/org/eclipse/jetty/jetty-servlets/9.4.28.v20200408/jetty-servlets-9.4.28.v20200408.jar:~/.m2/repository/org/clojure/tools.analyzer.jvm/0.7.2/tools.analyzer.jvm-0.7.2.jar:~/.m2/repository/org/tukaani/xz/1.8/xz-1.8.jar:~/.mx.cider/enrich-classpath/<shortened>.jar:~/.mx.cider/enrich-classpath/<shortened>.jar:")

(deftest middleware
  (let [all (sut/middleware* '{:repl-options         {:host "localhost"
                                                      :port 33421
                                                      :init (clojure.core/+)}
                               :source-paths         ["src"]
                               :test-paths           ["test"]
                               :resource-paths       ["resources"]
                               :plugins              [[refactor-nrepl "3.6.0"]]
                               :dependencies         [[puppetlabs/trapperkeeper-webserver-jetty9 "4.1.0"]]
                               :repositories         [["central" {:url "https://repo1.maven.org/maven2/" :snapshots false}]
                                                      ["clojars" {:url "https://repo.clojars.org/"}]]
                               :jvm-opts             ["-Dfoo=bar"
                                                      "-Xyz"]
                               :compile-path         "the-compile-path"
                               :managed-dependencies [[commons-codec "1.10"]
                                                      [org.slf4j/slf4j-api "1.7.25"]
                                                      [org.clojure/tools.reader "1.0.0-beta4"]]})

        [java cp classpath jvmopt1 jvmopt2 compile-path clojure-main eval-flag eval-code m nrepl host localhost port portno :as segments]
        (string/split all #"\s")

        classpath (-> classpath
                      (string/replace (System/getProperty "user.home") "~")
                      (string/replace "~/repo/" "~/enrich-classpath/")
                      (string/replace #".mx.cider/enrich-classpath/\d+/\d+/\d+.jar"
                                      ".mx.cider/enrich-classpath/<shortened>.jar"))]
    (testing all
      (is (= "java" java))
      (is (= "-cp" cp))
      (is (string/starts-with? classpath classpath-starts-with))
      (is (= "-Dfoo=bar" jvmopt1))
      (is (= "-Xyz" jvmopt2))
      (is (= "-Dclojure.compile.path=the-compile-path" compile-path))
      (is (= "clojure.main" clojure-main))
      (is (= "--eval" eval-flag))
      (is (= "\"(clojure.core/+)\"" eval-code))
      (is (= "-m" m))
      (is (= "nrepl.cmdline" nrepl))
      (is (= "--host" host))
      (is (= "\"localhost\"" localhost))
      (is (= "--port" port))
      (is (= "33421" portno)))))
