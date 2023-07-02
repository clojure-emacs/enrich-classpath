(defproject lein-sample "0.1.0"
  :repl-options {:port 33421}
  :plugins [[cider/cider-nrepl "0.31.0"] ;; should add its middleware
            [refactor-nrepl "3.6.0"] ;; should add its middleware
            [mx.cider/enrich-classpath ~(or (not-empty (System/getenv "PROJECT_VERSION"))
                                            "0.0.0")]]
  :middleware [cider.enrich-classpath.plugin-v2/middleware]
  :dependencies         [[puppetlabs/trapperkeeper-webserver-jetty9 "4.1.0"]]
  :managed-dependencies [[commons-codec "1.10"]
                         [org.slf4j/slf4j-api "1.7.25"]
                         [org.clojure/tools.reader "1.0.0-beta4"]])
