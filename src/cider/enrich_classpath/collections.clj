(ns cider.enrich-classpath.collections
  (:require
   [cider.enrich-classpath.logging :refer [warn]]
   [clojure.walk :as walk]))

(defn index [coll item]
  {:pre  [(vector? coll)]
   :post [(or (-> % long pos?) ;; note: there's no nat-int? in old versions of Lein
              (-> % long zero?))]}
  (->> coll
       (map-indexed (fn [i x]
                      (when (= x item)
                        i)))
       (filter some?)
       first))

(defn normalize-exclusions [exclusions]
  (assert (or (sequential? exclusions)
              ;; very unusual edge case:
              (set? exclusions))
          (pr-str exclusions))
  (->> exclusions
       (mapv (fn [x]
               (cond-> x
                 (not (vector? x)) vector)))))

(defn maybe-normalize* [x]
  (->> x
       (walk/postwalk (fn [item]
                        (cond-> item
                          (and (vector? item)
                               (some #{:exclusions} item))
                          (update (inc (long (index item :exclusions)))
                                  normalize-exclusions))))))

(def maybe-normalize (memoize maybe-normalize*))

(defn safe-sort
  "Guards against errors when comparing objects of different classes."
  [coll]
  (try
    (->> coll
         (sort (fn inner-compare [x y]
                 (try
                   (cond
                     (and (vector? x) (not (coll? y)))
                     (inner-compare x [y])

                     (and (vector? y) (not (coll? x)))
                     (inner-compare [x] y)

                     true
                     (->> [x y]
                          (map maybe-normalize)
                          (apply compare)))
                   (catch Exception e
                     (warn (pr-str [::could-not-sort x y]))
                     (when (System/getProperty "cider.enrich-classpath.throw")
                       (throw e))
                     0)))))
    (catch Exception e
      (warn (pr-str [::could-not-sort coll]))
      (when (System/getProperty "cider.enrich-classpath.throw")
        (throw e))
      coll)))

(defn ensure-no-lists* [x]
  {:pre [(vector? x)]}
  (->> x (mapv (fn [y]
                 (let [v (cond-> y
                           (sequential? y) vec)]
                   (cond-> v
                     (vector? v) ensure-no-lists*))))))

(def ensure-no-lists (memoize ensure-no-lists*))

(defn flatten-deps [xs]
  (->> xs
       (mapcat (fn [[k v]]
                 (apply list k v)))))

(defn add-exclusions-if-classified [coordinate]
  {:pre [(vector? coordinate)
         (not (vector? (first coordinate)))]}
  (let [catchall '[[*]]]

    (if-not (some #{:classifier} coordinate)
      coordinate
      (let [maybe-with-catchall-exclusions (cond-> coordinate
                                             (not (some #{:exclusions} coordinate))
                                             (conj :exclusions catchall))]
        (->> maybe-with-catchall-exclusions

             (reduce (fn [{:keys [found? result]} x]
                       (if found?
                         {:found? false
                          :result (conj result catchall)}
                         {:found? (= x :exclusions)
                          :result (conj result x)}))
                     {:found? false
                      :result []})

             (:result))))))

;; Vendored (and modified) code, for avoiding depending on clojure.spec
;; ...Lein can run old Clojure versions predating Spec.
(defn divide-by
  "Divides `coll` in `n` parts. The parts can have disparate sizes if the division isn't exact."
  {:author  "https://github.com/nedap/utils.collections"
   :license "Eclipse Public License 2.0"}
  [^long n coll]
  (let [the-count (count coll)
        seed [(-> the-count double (/ n) Math/floor)
              (rem the-count n)
              []
              coll]
        recipe (iterate (fn [[quotient remainder output input]]
                          (let [remainder (long remainder)
                                quotient (long quotient)
                                chunk-size (+ quotient (if (pos? remainder)
                                                         1
                                                         0))
                                addition (take chunk-size input)
                                result (cond-> output
                                         (seq addition) (conj addition))]
                            [quotient
                             (dec remainder)
                             result
                             (drop chunk-size input)]))
                        seed)
        index (inc n)]
    (-> recipe
        (nth index)
        (nth 2))))
