(ns cider.enrich-classpath.jar
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string])
  (:import
   (cider.enrich_classpath Calc72)
   (java.io ByteArrayOutputStream File PrintStream)
   (java.util.jar JarOutputStream Manifest)
   (java.util.zip CRC32)))

(defn wrap72 [s]
  (let [b (ByteArrayOutputStream.)]
    (Calc72/calc72 (PrintStream. b) s)
    (str b)))

(defn jars->classpath [jars]
  (wrap72 (str "Class-Path: "
               (string/join " " jars))))

(def template
  "Manifest-Version: 1.0
%s
Created-By: mx.cider/enrich-classpath
")

(defn manifest ^String [classpath]
  (format template classpath))

(defn jar-for! [jars]
  (when-let [corpus (->> jars
                         ;; nil values should never be included (`integration_test.clj` asserts this),
                         ;; but just in case:
                         (filter some?)
                         ;; maybe there's nothing to enrich in a small-enough project:
                         (seq))]
    (let [sha-bytes (-> corpus string/join .getBytes)
          sha (-> (CRC32.) (doto (.update sha-bytes 0 (alength sha-bytes))) .getValue)
          filename (str sha ".jar")]

      (when-not (-> filename File. .exists)
        (let [manifest-contents (-> corpus jars->classpath manifest)
              manifest (-> manifest-contents (.getBytes "UTF-8") io/input-stream Manifest.)]
          (-> filename io/output-stream (JarOutputStream. manifest) (.close))))

      filename)))
