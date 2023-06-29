(def project-version (if (System/getenv "CIRCLE_TAG")
                       (doto (System/getenv "PROJECT_VERSION") assert)
                       (or (not-empty (System/getenv "PROJECT_VERSION"))
                           "n/a")))

(defproject mx.cider/tools.deps.enrich-classpath project-version
  :description "mx.cider/enrich-classpath with dependencies specific to tools.deps usage."
  :url "https://github.com/clojure-emacs/enrich-classpath"
  :license {:name "EPL-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}

  :pedantic? ~(if (System/getenv "CI")
                :abort
                ;; :pedantic? can be problematic for certain local dev workflows:
                false)

  :dependencies [[mx.cider/enrich-classpath ~project-version]
                 [org.clojure/clojure "1.11.1"]
                 [org.clojure/tools.deps "0.18.1354"]
                 [org.apache.maven/maven-resolver-provider "3.8.7"]
                 [org.codehaus.plexus/plexus-utils "3.4.1"]
                 [org.apache.maven.resolver/maven-resolver-transport-http "1.9.4"]
                 [org.apache.maven.resolver/maven-resolver-impl "1.9.4"]
                 [javax.annotation/javax.annotation-api "1.3.2"]
                 [org.apache.maven/maven-builder-support "3.8.7"]
                 [org.apache.maven.resolver/maven-resolver-transport-file "1.9.4"]
                 [org.apache.maven/maven-artifact "3.8.7"]
                 [org.apache.maven.resolver/maven-resolver-connector-basic "1.9.4"]
                 [org.apache.maven.resolver/maven-resolver-util "1.9.4"]
                 [org.apache.maven.resolver/maven-resolver-spi "1.9.4"]
                 [org.apache.maven.resolver/maven-resolver-api "1.9.4"]
                 [org.apache.httpcomponents/httpclient "4.5.14"]
                 [org.apache.httpcomponents/httpcore "4.4.16"]
                 [clj-commons/pomegranate "1.2.23"]]
  :profiles {:eastwood {:plugins [[jonase/eastwood "1.4.0"]]
                        :eastwood {:add-linters [:boxed-math
                                                 :performance]}}}
  :deploy-repositories [["clojars" {:url "https://clojars.org/repo"
                                    :username :env/clojars_username
                                    :password :env/clojars_password
                                    :sign-releases false}]])
