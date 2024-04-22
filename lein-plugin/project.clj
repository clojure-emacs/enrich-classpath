(def project-version (if (System/getenv "CIRCLE_TAG")
                       (doto (System/getenv "PROJECT_VERSION") assert)
                       (or (not-empty (System/getenv "PROJECT_VERSION"))
                           "RELEASE")))

(defproject mx.cider/lein-enrich-classpath project-version
  :description "mx.cider/enrich-classpath with dependencies specific to tools.deps usage."
  :url "https://github.com/clojure-emacs/enrich-classpath"
  :license {:name "EPL-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}

  :dependencies [[mx.cider/enrich-classpath ~project-version]
                 [org.clojure/clojure "1.11.1"]]
  :pedantic? ~(if (System/getenv "CI")
                :abort
                ;; :pedantic? can be problematic for certain local dev workflows:
                false)
  :profiles {:dev      {:source-paths ["../src" "../test"]}
             :test     {:dependencies [[leiningen "2.10.0" :exclusions [nrepl]]]}
             :eastwood {:plugins  [[jonase/eastwood "1.4.0"]]
                        :eastwood {:add-linters [:boxed-math
                                                 :performance]}}}
  :deploy-repositories [["clojars" {:url           "https://clojars.org/repo"
                                    :username      :env/clojars_username
                                    :password      :env/clojars_password
                                    :sign-releases false}]])
