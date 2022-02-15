(ns cider.enrich-classpath.version)

(def data-version 1)

(defn outdated-data-version? [m]
  (-> m
      :enrich-classpath/version ;; can be nil (handling maps before versioning was introduced)
      (not= data-version)))
