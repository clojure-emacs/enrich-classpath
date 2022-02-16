(ns cider.enrich-classpath.jar
  (:require
   [cider.enrich-classpath.jdk :as jdk]
   [clojure.java.io :as io]
   [clojure.string :as string])
  (:import
   (cider.enrich_classpath Calc72)
   (java.io ByteArrayOutputStream File PrintStream)
   (java.util.jar JarInputStream JarOutputStream Manifest)
   (java.util.zip CRC32)))

;; Manifest files must be wrapped every 72 lines, with one space of padding for every inserted newline.
;; In principle the `Manifest` class provides this wrapping already, however beyond certain input size,
;; it will throw an exception.
;; An already-wrapped string will be accepted by `Manifest` without altering it or throwing any exception.
(defn wrap72 [s]
  (let [b (ByteArrayOutputStream.)]
    (Calc72/calc72 (PrintStream. b)
                   s
                   (boolean (re-find #"^1\.8\." (System/getProperty "java.version"))))
    (str b)))

(defn crc32 [^String s]
  (let [s-bytes (-> s .getBytes)]
    (-> (CRC32.) (doto (.update s-bytes 0 (alength s-bytes))) .getValue)))

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

(defn jar-file->manifest-contents [^File file]
  (let [os (ByteArrayOutputStream.)]
    (-> file io/input-stream JarInputStream. .getManifest (.write os))
    (-> os .toByteArray (String. "UTF-8"))))

(defn jar-for! ^String [jars]
  (when-let [corpus (->> jars
                         ;; nil values should never be included (`integration_test.clj` asserts this),
                         ;; but just in case:
                         (filter some?)
                         ;; maybe there's nothing to enrich in a small-enough project:
                         (seq))]
    (let [corpus-crc (-> corpus string/join crc32)
          dir-crc (-> "user.dir" System/getProperty crc32 str)
          dir (-> "user.home"
                  System/getProperty
                  (io/file ".mx.cider" "enrich-classpath" (jdk/digits-str) dir-crc)
                  (doto .mkdirs))
          filename (-> dir
                       (io/file (str corpus-crc ".jar"))
                       str)]

      (when-not (-> filename File. .exists)
        (let [manifest-contents (-> corpus jars->classpath manifest)
              manifest (-> manifest-contents (.getBytes "UTF-8") io/input-stream Manifest.)]
          (-> filename io/output-stream (JarOutputStream. manifest) (.close))))

      filename)))
