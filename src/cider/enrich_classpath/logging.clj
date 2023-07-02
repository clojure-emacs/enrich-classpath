(ns cider.enrich-classpath.logging
  (:import
   (clojure.lang IFn)))

;; These logging helpers ease developing the plugin itself
;; (since leiningen.core cannot be required in rich repls, deps.edn projects, etc)

(def lein? (try
             (require 'leiningen.core.main)
             true
             (catch Exception e
               false)))

(def debug-lock (Object.))

(def info-lock (Object.))

(def warn-lock (Object.))

(defn debug [x]
  (locking debug-lock
    (if lein?
      (-> 'leiningen.core.main/debug ^IFn (resolve) (.invoke x))
      (println x))))

(defn info [x]
  (locking info-lock
    (if lein?
      (-> 'leiningen.core.main/info ^IFn (resolve) (.invoke x))
      (println x))))

(defn warn [x]
  (if lein?
    (locking warn-lock
      (-> 'leiningen.core.main/warn ^IFn (resolve) (.invoke x))
      (println x))))
