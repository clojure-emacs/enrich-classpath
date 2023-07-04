(def project-version (or (not-empty (System/getenv "PROJECT_VERSION"))
                         "0.0.0"))

(defproject mx.cider/lein-enrich-classpath project-version
  :dependencies [[mx.cider/enrich-classpath ~project-version]
                 [org.clojure/clojure "1.11.1"]]
  :pedantic? ~(if (System/getenv "CI")
                :abort
                ;; :pedantic? can be problematic for certain local dev workflows:
                false)
  :profiles {:test     {:dependencies [[leiningen-core "2.10.0"]]}
             :eastwood {:plugins  [[jonase/eastwood "1.4.0"]]
                        :eastwood {:add-linters [:boxed-math
                                                 :performance]}}}
  :deploy-repositories [["clojars" {:url           "https://clojars.org/repo"
                                    :username      :env/clojars_username
                                    :password      :env/clojars_password
                                    :sign-releases false}]])
