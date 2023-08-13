(ns cider.enrich-classpath.plugin-v2
  (:refer-clojure :exclude [time])
  (:require
   [leiningen.core.classpath :as leiningen.classpath]
   [cider.enrich-classpath :as enrich-classpath]
   [cider.enrich-classpath.logging :refer [debug info warn]]
   [leiningen.core.main]
   [clojure.string :as string]
   [clojure.edn :as edn]))

(defn format-nrepl-options [{:keys [transport nrepl-handler socket nrepl-middleware host port]}]
  (->> [["--transport" (when (qualified-symbol? transport)
                         (pr-str (str transport)))]
        ["--handler" (when (qualified-symbol? nrepl-handler)
                       (pr-str (str nrepl-handler)))]
        ["--socket" (some-> socket not-empty pr-str)]
        ["--middleware" (when-let [s (some->> nrepl-middleware
                                              (filterv qualified-symbol?)
                                              (not-empty)
                                              (pr-str))]
                          (str "\"" s "\""))]
        ["--host" (some-> host not-empty pr-str)]
        ["--port" (some-> port str not-empty)]]
       (filter second)
       (reduce into [])
       (string/join " ")))

(defn format-jvm-opts [{:keys [jvm-opts compile-path]}]
  (let [a jvm-opts
        b (some->> compile-path
                   not-empty
                   string/trim
                   (str "-Dclojure.compile.path="))
        all (cond-> a
              b (conj b))
        res (not-empty (string/join " " all))]
    (if res
      (str " " res " ")
      "")))

(defn wrap-silently [init]
  (list `do (list `with-out-str (list `binding [`*err* `*out*] init)) nil))

(defn middleware* [{:keys [repl-options] :as project}]
  (let [java (or (some-> project :java not-empty string/trim)
                 "java")
        nrepl-options (format-nrepl-options repl-options)
        sep (System/getProperty "path.separator")
        orig (cond-> project
               ;; XXX condition could be better: instead of this, resolve deps+mged-deps, check all of them (i.e. the transitives ones)
               (not-any? (comp '#{nrepl/nrepl nrepl} first) (:dependencies project))
               (update :dependencies conj '[nrepl/nrepl "1.0.0"])

               true
               (assoc :pedantic? false)

               true
               leiningen.classpath/get-classpath)
        orig (string/join sep orig)
        {{{:keys [tools
                  dependencies
                  managed-dependencies
                  java-source-paths
                  jdk-sources]} :results} :enrich-classpath}
        (enrich-classpath/middleware (-> project
                                         (assoc-in [:enrich-classpath :shorten] true)
                                         (assoc-in [:enrich-classpath :only-present-results?] true)))

        suffix
        (->> [dependencies
              managed-dependencies
              java-source-paths
              tools
              jdk-sources]
             (reduce into [])
             (remove empty?)
             (string/join sep))

        enriched-classpath (str orig sep suffix)
        {:keys [init]} repl-options
        init-form (or (when (and init
                                 (try
                                   (not (empty? init))
                                   (catch Exception _
                                     true)))
                        (str " --eval "
                             (pr-str (pr-str (wrap-silently init)))
                             " "))
                      " ")]
    (format "%s -cp %s%sclojure.main%s-m nrepl.cmdline %s"
            java
            enriched-classpath
            (format-jvm-opts project)
            init-form
            nrepl-options)))

(defn middleware [project]
  ;; XXX failsafe. as a last resource, `echo` the strace
  (println (middleware* project))
  ;; XXX exit 1 too
  (leiningen.core.main/exit 0))

;; export PROJECT_VERSION=0.0.0
;; make install
;; cd lein-sample
;; lein repl
