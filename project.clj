(defproject mx.cider/enrich-classpath (or (not-empty (System/getenv "PROJECT_VERSION"))
                                          "0.0.0")
  :description "Makes available .jars with Java sources and javadocs for a given project."

  :url "https://github.com/clojure-emacs/enrich-classpath"

  :license {:name "EPL-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}

  :dependencies [^:inline-dep [fipp "0.6.25" :exclusions [org.clojure/clojure]]
                 [org.clojure/clojure "1.10.3"] ;; Hard-require a recent-enough version of Clojure, since other plugins may require an overly old one which would make Fipp fail.
                 ]

  :eval-in-leiningen ~(nil? (System/getenv "no_eval_in_leiningen"))

  :plugins [[thomasa/mranderson "0.5.4-SNAPSHOT"]]

  :jvm-opts ["-Dclojure.main.report=stderr"]

  :mranderson {:project-prefix  "cider.enrich-classpath.inlined-deps"
               :expositions     []
               :unresolved-tree false}

  :deploy-repositories [["clojars" {:url "https://clojars.org/repo"
                                    :username :env/clojars_username
                                    :password :env/clojars_password
                                    :sign-releases false}]]

  :java-source-paths ["java"]

  :profiles {;; Helps developing the plugin when (false? eval-in-leiningen):
             :test                {:dependencies [[clj-commons/pomegranate "1.2.1"]]}

             :integration-testing {:source-paths ["integration-testing"]}

             :self-test           {:middleware   [cider.enrich-classpath/middleware]
                                   ;; ensure that at least one dependency will fetch sources:
                                   :dependencies [[puppetlabs/trapperkeeper-webserver-jetty9 "4.1.0"]]}

             :eastwood            {:plugins [[jonase/eastwood "1.2.2"]]
                                   :eastwood {:add-linters [:boxed-math
                                                            :performance]}}

             :deploy              {:source-paths [".circleci"]}}

  :aliases {"integration-test" ["with-profile" "-user,-dev,+test,+integration-testing" "run" "-m" "integration-test"]})
