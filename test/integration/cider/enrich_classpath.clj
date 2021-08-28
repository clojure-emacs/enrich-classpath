(ns integration.cider.enrich-classpath
  (:require
   [cider.enrich-classpath :as sut]
   [cider.enrich-classpath.locks :as locks]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.test :refer [are deftest is testing]])
  (:import
   (java.io File)))

(deftest read-file!
  (testing "Reads file contents"
    (are [input expected] (testing input
                            (is (= expected
                                   (-> input io/resource io/as-file str locks/read-file!)))
                            true)
      "integration/cider/foo.txt" "42\n")))

(deftest write-file!
  (testing "Writes file contents, while also using a merge function. Sort order is stable"
    (let [filename "test/integration/cider/bar.txt"
          file (-> filename File.)
          state (atom {})]
      (-> file .createNewFile)
      (try
        (are [input expected] (testing input
                                (let [v (locks/write-file! filename
                                                           (sut/make-merge-fn state))]

                                  (is (= expected v))
                                  (is (= expected (locks/read-file! filename))))
                                true)
          (swap! state assoc [[4]] {[5 6] nil}) "[[[[4]] [[[5 6] nil]]]]\n"
          (swap! state assoc [[1]] {[2 3] nil}) "[[[[1]] [[[2 3] nil]]] [[[4]] [[[5 6] nil]]]]\n")
        (finally
          (-> file .delete))))))

(deftest serialize-deserialize
  (let [filename "test/integration/cider/sample.edn"
        file (-> filename File. slurp)
        form (-> file read-string)
        serialized (-> form sut/serialize)]
    (is (< 100
           (count form))
        "Sanity check")
    (is (not= form serialized))
    (is (= form
           (-> serialized sut/deserialize)))))

(deftest acceptable-repository?
  (are [desc input expected] (testing [desc input]
                               (is (= expected
                                      (sut/acceptable-repository? [:_ input])))
                               true)
    "Basic case"
    {:url "https://example.com"}
    true

    "Rejects entries having passwords, as they generally won't have source .jar (affecting performance)"
    {:url      "https://example.com"
     :password "foo"}
    false)

  (are [desc input expected] (testing [desc input]
                               (is (= expected
                                      (sut/acceptable-repository? [:_ {:url input}])))
                               true)
    "Basic case"
    "https://example.com"         true

    "Rejects non-existing domains, as they cause timeouts"
    "https://example.foooooooooo" false

    "Rejects unencrypted HTTP, as Lein would reject it"
    "http://example.com"          false

    "Rejects git repositories (as used by some plugins),
since a git repo inherently cannot resolve to a .jar artifact"
    "git://github.com"            false))

(deftest derivatives
  (testing "Works recursively, fetching sources for transitive dependencies that might be multiple levels away"
    (let [repositories {"central" {:url       "https://repo1.maven.org/maven2/"
                                   :snapshots false}
                        "clojars" {:url "https://repo.clojars.org/"}}
          classifiers #{"sources"}
          cache-atom (atom {})
          memoized-resolve! (memoize (partial sut/resolve! cache-atom repositories classifiers))]
      (are [input expected] (testing input
                              (let [sw (java.io.StringWriter.)
                                    v (binding [*out* sw]
                                        (sort (sut/derivatives classifiers
                                                               []
                                                               memoized-resolve!
                                                               input)))
                                    s (str sw)]
                                (assert (not (string/includes? s "timed-out")))
                                (is (= expected
                                       v)
                                    s)
                                true))
        []                                                   [[]]
        '[puppetlabs/trapperkeeper-webserver-jetty9]         '[[puppetlabs/trapperkeeper-webserver-jetty9]]
        '[puppetlabs/trapperkeeper-webserver-jetty9 "4.1.0"] '[[beckon "0.1.1"]
                                                               [cheshire "5.8.0"]
                                                               [clj-time "0.11.0"]
                                                               [commons-codec "1.6"]
                                                               [cpath-clj "0.1.2"]
                                                               [digest "1.4.3"]
                                                               [joda-time "2.8.2"]
                                                               [nrepl "0.6.0"]
                                                               [slingshot "0.12.2"]
                                                               [tigris "0.1.1"]
                                                               [ch.qos.logback/logback-access "1.2.3"]
                                                               [ch.qos.logback/logback-classic "1.2.3"]
                                                               [ch.qos.logback/logback-core "1.2.3"]
                                                               [clj-commons/clj-yaml "0.7.0"]
                                                               [clj-commons/fs "1.5.1"]
                                                               [com.fasterxml.jackson.core/jackson-core "2.9.0"]
                                                               [com.fasterxml.jackson.dataformat/jackson-dataformat-cbor "2.9.0"]
                                                               [com.fasterxml.jackson.dataformat/jackson-dataformat-smile "2.9.0"]
                                                               [com.typesafe/config "1.2.0"]
                                                               [javax.servlet/javax.servlet-api "3.1.0"]
                                                               [org.apache.commons/commons-compress "1.18"]
                                                               [org.clojure/clojure "1.10.1"]
                                                               [org.clojure/core.async "0.4.490"]
                                                               [org.clojure/core.cache "0.6.5"]
                                                               [org.clojure/core.memoize "0.5.9"]
                                                               [org.clojure/core.specs.alpha "0.2.44"]
                                                               [org.clojure/data.priority-map "0.0.7"]
                                                               [org.clojure/java.classpath "0.2.2"]
                                                               [org.clojure/java.jmx "0.3.4"]
                                                               [org.clojure/spec.alpha "0.2.176"]
                                                               [org.clojure/tools.analyzer "0.6.9"]
                                                               [org.clojure/tools.analyzer.jvm "0.7.2"]
                                                               [org.clojure/tools.cli "0.3.6"]
                                                               [org.clojure/tools.logging "0.4.0"]
                                                               [org.clojure/tools.macro "0.1.5"]
                                                               [org.clojure/tools.reader "0.7.2"]
                                                               [org.codehaus.janino/commons-compiler "3.0.8"]
                                                               [org.codehaus.janino/janino "3.0.8"]
                                                               [org.eclipse.jetty/jetty-client "9.4.28.v20200408"]
                                                               [org.eclipse.jetty/jetty-continuation "9.4.28.v20200408"]
                                                               [org.eclipse.jetty/jetty-http "9.4.28.v20200408"]
                                                               [org.eclipse.jetty/jetty-io "9.4.28.v20200408"]
                                                               [org.eclipse.jetty/jetty-jmx "9.4.28.v20200408"]
                                                               [org.eclipse.jetty/jetty-proxy "9.4.28.v20200408"]
                                                               [org.eclipse.jetty/jetty-security "9.4.28.v20200408"]
                                                               [org.eclipse.jetty/jetty-servlet "9.4.28.v20200408"]
                                                               [org.eclipse.jetty/jetty-servlets "9.4.28.v20200408"]
                                                               [org.eclipse.jetty/jetty-util "9.4.28.v20200408"]
                                                               [org.eclipse.jetty/jetty-webapp "9.4.28.v20200408"]
                                                               [org.eclipse.jetty/jetty-xml "9.4.28.v20200408"]
                                                               [org.eclipse.jetty.websocket/websocket-api "9.4.28.v20200408"]
                                                               [org.eclipse.jetty.websocket/websocket-client "9.4.28.v20200408"]
                                                               [org.eclipse.jetty.websocket/websocket-common "9.4.28.v20200408"]
                                                               [org.eclipse.jetty.websocket/websocket-server "9.4.28.v20200408"]
                                                               [org.eclipse.jetty.websocket/websocket-servlet "9.4.28.v20200408"]
                                                               [org.flatland/ordered "1.5.7"]
                                                               [org.flatland/useful "0.11.6"]
                                                               [org.gnu.gettext/libintl "0.18.3"]
                                                               [org.ini4j/ini4j "0.5.2"]
                                                               [org.ow2.asm/asm-all "4.2"]
                                                               [org.slf4j/jul-to-slf4j "1.7.20"]
                                                               [org.slf4j/log4j-over-slf4j "1.7.20"]
                                                               [org.slf4j/slf4j-api "1.7.20"]
                                                               [org.tcrawley/dynapath "0.2.5"]
                                                               [org.tukaani/xz "1.8"]
                                                               [org.yaml/snakeyaml "1.24"]
                                                               [prismatic/plumbing "0.4.2"]
                                                               [prismatic/schema "1.1.9"]
                                                               [puppetlabs/i18n "0.8.0"]
                                                               [puppetlabs/kitchensink "3.0.0"]
                                                               [puppetlabs/ssl-utils "3.0.4"]
                                                               [puppetlabs/trapperkeeper "3.0.0"]
                                                               [puppetlabs/trapperkeeper-filesystem-watcher "1.2.0"]
                                                               [puppetlabs/trapperkeeper-webserver-jetty9 "4.1.0"]
                                                               [puppetlabs/typesafe-config "0.1.5"]
                                                               [ring/ring-codec "1.0.0"]
                                                               [ring/ring-servlet "1.5.0"]
                                                               [commons-codec "1.6" :classifier "sources"]
                                                               [joda-time "2.8.2" :classifier "sources"]
                                                               [ch.qos.logback/logback-access "1.2.3" :classifier "sources"]
                                                               [ch.qos.logback/logback-classic "1.2.3" :classifier "sources"]
                                                               [ch.qos.logback/logback-core "1.2.3" :classifier "sources"]
                                                               [com.fasterxml.jackson.core/jackson-core
                                                                "2.9.0"
                                                                :classifier
                                                                "sources"]
                                                               [com.fasterxml.jackson.dataformat/jackson-dataformat-cbor
                                                                "2.9.0"
                                                                :classifier
                                                                "sources"]
                                                               [com.fasterxml.jackson.dataformat/jackson-dataformat-smile
                                                                "2.9.0"
                                                                :classifier
                                                                "sources"]
                                                               [com.typesafe/config "1.2.0" :classifier "sources"]
                                                               [de.kotka/lazymap "3.1.0" :exclusions [[org.clojure/clojure]]]
                                                               [javax.servlet/javax.servlet-api "3.1.0" :classifier "sources"]
                                                               [org.apache.commons/commons-compress "1.18" :classifier "sources"]
                                                               [org.clojure/clojure "1.10.1" :classifier "sources"]
                                                               [org.codehaus.janino/commons-compiler "3.0.8" :classifier "sources"]
                                                               [org.codehaus.janino/janino "3.0.8" :classifier "sources"]
                                                               [org.eclipse.jetty/jetty-client
                                                                "9.4.28.v20200408"
                                                                :classifier
                                                                "sources"]
                                                               [org.eclipse.jetty/jetty-continuation
                                                                "9.4.28.v20200408"
                                                                :classifier
                                                                "sources"]
                                                               [org.eclipse.jetty/jetty-http
                                                                "9.4.28.v20200408"
                                                                :classifier
                                                                "sources"]
                                                               [org.eclipse.jetty/jetty-io
                                                                "9.4.28.v20200408"
                                                                :classifier
                                                                "sources"]
                                                               [org.eclipse.jetty/jetty-jmx
                                                                "9.4.28.v20200408"
                                                                :classifier
                                                                "sources"]
                                                               [org.eclipse.jetty/jetty-proxy
                                                                "9.4.28.v20200408"
                                                                :classifier
                                                                "sources"]
                                                               [org.eclipse.jetty/jetty-security
                                                                "9.4.28.v20200408"
                                                                :classifier
                                                                "sources"]
                                                               [org.eclipse.jetty/jetty-server
                                                                "9.4.28.v20200408"
                                                                :classifier
                                                                "sources"]
                                                               [org.eclipse.jetty/jetty-server
                                                                "9.4.28.v20200408"
                                                                :exclusions
                                                                [[org.eclipse.jetty.orbit/javax.servlet]]]
                                                               [org.eclipse.jetty/jetty-servlet
                                                                "9.4.28.v20200408"
                                                                :classifier
                                                                "sources"]
                                                               [org.eclipse.jetty/jetty-servlets
                                                                "9.4.28.v20200408"
                                                                :classifier
                                                                "sources"]
                                                               [org.eclipse.jetty/jetty-util
                                                                "9.4.28.v20200408"
                                                                :classifier
                                                                "sources"]
                                                               [org.eclipse.jetty/jetty-webapp
                                                                "9.4.28.v20200408"
                                                                :classifier
                                                                "sources"]
                                                               [org.eclipse.jetty/jetty-xml
                                                                "9.4.28.v20200408"
                                                                :classifier
                                                                "sources"]
                                                               [org.eclipse.jetty.websocket/websocket-api
                                                                "9.4.28.v20200408"
                                                                :classifier
                                                                "sources"]
                                                               [org.eclipse.jetty.websocket/websocket-client
                                                                "9.4.28.v20200408"
                                                                :classifier
                                                                "sources"]
                                                               [org.eclipse.jetty.websocket/websocket-common
                                                                "9.4.28.v20200408"
                                                                :classifier
                                                                "sources"]
                                                               [org.eclipse.jetty.websocket/websocket-server
                                                                "9.4.28.v20200408"
                                                                :classifier
                                                                "sources"]
                                                               [org.eclipse.jetty.websocket/websocket-servlet
                                                                "9.4.28.v20200408"
                                                                :classifier
                                                                "sources"]
                                                               [org.gnu.gettext/libintl "0.18.3" :classifier "sources"]
                                                               [org.ini4j/ini4j "0.5.2" :classifier "sources"]
                                                               [org.ow2.asm/asm-all "4.2" :classifier "sources"]
                                                               [org.slf4j/jul-to-slf4j "1.7.20" :classifier "sources"]
                                                               [org.slf4j/log4j-over-slf4j "1.7.20" :classifier "sources"]
                                                               [org.slf4j/slf4j-api "1.7.20" :classifier "sources"]
                                                               [org.tukaani/xz "1.8" :classifier "sources"]
                                                               [org.yaml/snakeyaml "1.24" :classifier "sources"]
                                                               [puppetlabs/ssl-utils "3.0.4" :classifier "sources"]]))))

(deftest wrap-failsafe
  (let [project {::project ::project}]

    (are [input expected] (testing input
                            (is (= expected
                                   (let [silently--old System/out
                                         silently--pw (java.io.PrintWriter. "/dev/null")
                                         silently--ps (java.io.PrintStream. (proxy [java.io.OutputStream] []
                                                                              (write
                                                                                ([a])
                                                                                ([a b c])
                                                                                ([a b c d e]))))]
                                     (binding [*out* silently--pw
                                               *err* silently--pw]
                                       (try
                                         (System/setOut silently--ps)
                                         (System/setErr silently--ps)
                                         ((sut/wrap-failsafe input 1) project)
                                         (finally
                                           (System/setOut silently--old)
                                           (System/setErr silently--old)))))))
                            true)
      (fn [project]
        (assoc project ::foo 42)) (assoc project ::foo 42)

      (fn [project]
        (Thread/sleep 1500))      project

      (fn [project]
        (assert false))           project

      (fn [project]
        (throw (ex-info "." {}))) project)))
