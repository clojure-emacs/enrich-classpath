(ns cider.enrich-classpath.plugin-v2
  (:refer-clojure :exclude [time])
  (:require
   [cider.enrich-classpath :as enrich-classpath]
   [cider.enrich-classpath.jdk :as jdk]
   [cider.enrich-classpath.logging :refer [debug info warn]]
   [clojure.string :as string]
   [leiningen.core.classpath :as leiningen.classpath]
   [leiningen.core.main]
   [leiningen.javac :as javac]))

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
              b (conj b)
              true jdk/maybe-add-opens)
        res (not-empty (string/join " " all))]
    (if res
      (str " " res " ")
      "")))

(defn wrap-try
  "Wraps `init` in a try/catch because otherwise `clojure.main`
  can fail at startup, while 'init' errors can often be remediated by the user later."
  [init-ns init global-vars]
  ;; Note that we don't need to suppress output (I formerly did that for stdout/stderr).
  ;; This code runs after the classpath has been enriched and the `java` command has been generated.
  (let [{clojure-global-vars true
         other-global-vars   false} (group-by (fn [[sym]]
                                                (contains? #{nil "clojure.core"} (namespace sym)))
                                              global-vars)]
    (apply list `try (reduce into [] [(mapv (fn [[var-sym value]]
                                              (list `set! var-sym value))
                                            clojure-global-vars)
                                      (when init-ns
                                        [(list `doto (list `quote init-ns) `require `in-ns)])
                                      (mapv (fn [[var-sym value]]
                                              (list `set! var-sym value))
                                            other-global-vars)
                                      (when init
                                        [init])
                                      [(list `catch `Throwable 'e
                                             (list '.printStackTrace 'e))]]))))

(defn build-init-form [{{:keys [init init-ns]} :repl-options
                        :keys [global-vars]}]
  (or (when (or init init-ns (seq global-vars))
        (str " --eval "
             (pr-str (pr-str (wrap-try init-ns init global-vars)))
             " "))
      " "))

(defn remove-enrich-middleware [mw]
  (into []
        (remove #{`middleware
                  'cider.enrich-classpath/middleware})
        mw))

(defn remove-enrich-middleware-from-map [m]
  (into {}
        (map (fn [[profile-name profile-content]]
               [profile-name (cond-> profile-content
                               (and (map? profile-content) ;; avoid hitting composite profiles
                                    (:middleware profile-content))
                               (update :middleware remove-enrich-middleware))]))
        m))

(defn middleware* [{:keys [repl-options java-source-paths] :as project}]

  (when (seq java-source-paths)
    (binding [leiningen.core.main/*exit-process?* false]
      (warn "enrich-classpath has triggered javac.")
      (javac/javac (with-meta (-> project
                                  (update :middleware remove-enrich-middleware)
                                  (update :profiles remove-enrich-middleware-from-map))
                     (-> project
                         meta
                         (update-in [:without-profiles :middleware] remove-enrich-middleware)
                         (update-in [:without-profiles :profiles] remove-enrich-middleware-from-map)
                         (update :profiles remove-enrich-middleware-from-map))))))

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
        init-form (build-init-form project)]
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
