(ns cider.enrich-classpath.logging
  (:import
   (clojure.lang IFn)))

(def info-lock (Object.))

;; These logging helpers ease developing the plugin itself
;; (since leiningen.core cannot be required in rich repls, deps.edn projects, etc)
(def debug-lock (Object.))

(defn debug [x]
  (locking debug-lock
    (try
      (require 'leiningen.core.main)
      (-> 'leiningen.core.main/debug ^IFn resolve (.invoke x))
      (catch Exception e
        (println x)))))

(defn info [x]
  (locking info-lock
    (try
      (require 'leiningen.core.main)
      (-> 'leiningen.core.main/info ^IFn resolve (.invoke x))
      (catch Exception e
        (println x)))))

(def warn-lock (Object.))

(defn warn [x]
  (locking warn-lock
    (try
      (require 'leiningen.core.main)
      (-> 'leiningen.core.main/warn ^IFn resolve (.invoke x))
      (catch Exception e
        (println x)))))
