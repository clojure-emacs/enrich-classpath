(ns cider.enrich-classpath.version)

(def data-version 1)

(defn long-not= [^long x, ^long y] ;; coerces to primitive long at the edges
  (not= x y))

(defn outdated-data-version? [m]
  (-> m
      :enrich-classpath/version ;; can be nil (handling maps before versioning was introduced)
      (or 0)
      (long-not= data-version)))
