(ns cider.enrich-classpath.xdg
  (:require
   [clojure.string :as string]
   [clojure.java.io :as io])
  (:import
   (java.io File)))

(defn maybe-as-absolute [x]
  (try
    (let [expanded (-> x (string/replace-first #"^~" (System/getProperty "user.home")))
          file (io/file expanded)]
      (when (-> file .isAbsolute)
        (-> file .mkdirs)
        (when (-> file .canWrite)
          (-> file .getCanonicalPath))))
    (catch Exception _
      nil)))

(def ^String cache-root
  (or (some-> "XDG_CACHE_HOME"
              System/getenv
              not-empty
              maybe-as-absolute)
      (maybe-as-absolute "~/.cache")))
