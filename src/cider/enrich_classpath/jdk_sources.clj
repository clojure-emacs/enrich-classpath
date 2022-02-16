(ns cider.enrich-classpath.jdk-sources
  (:require
   [cider.enrich-classpath.jdk :as jdk]
   [cider.enrich-classpath.locks :refer [locking-file]]
   [cider.enrich-classpath.logging :refer [warn]]
   [clojure.java.io :as io]
   [clojure.string :as string])
  (:import
   (java.io File FileOutputStream)
   (java.net JarURLConnection URL)
   (java.util.zip ZipInputStream)))

(defmacro while-let
  {:style/indent 1}
  [[sym expr] & body]
  `(loop [~sym ~expr]
     (when ~sym
       ~@body
       (recur ~expr))))

(defn jdk-find [f]
  (let [home (io/file (System/getProperty "java.home"))
        parent (.getParentFile home)
        paths [(io/file home f)
               (io/file home "lib" f)
               (io/file parent f)
               (io/file parent "lib" f)]
        v (some->> paths
                   (filter #(.canRead ^File %))
                   first
                   str)]
    (when-not v
      (warn :cider.enrich-classpath/no-jdk-sources-found))
    v))

(def base-prefix ".mx.cider")

(defn external-resource ^URL [path]
  (when-let [obj (io/resource path)]
    (when-not (string/includes? (str obj) base-prefix)
      obj)))

(defn java-path->zip-path [path]
  (some-> (external-resource path)
          ^JarURLConnection (. openConnection)
          .getJarFileURL
          io/as-file
          str))

(def jdk-sources
  (delay
    (or (java-path->zip-path "java.base/java/lang/Object.java") ;; JDK9+
        (java-path->zip-path "java/lang/Object.java") ;; JDK8-
        (jdk-find "src.zip"))))

(def zip-separator
  ;; NOTE - does not necessarily equal `File/separator`:
  #"/")

(defn uncompress [^String prefix-path target]
  (let [zis (-> target io/input-stream ZipInputStream.)]
    (while-let [entry (-> zis .getNextEntry)]
      (let [size (-> entry .getSize)
            bytes (byte-array 1024)
            dest (->> entry .getName (io/file prefix-path))
            dir (-> entry .getName (string/split zip-separator) butlast)
            _ (->> (string/join File/separator dir)
                   (File. prefix-path)
                   .mkdirs)
            output (FileOutputStream. dest)]
        (try
          (loop [len (-> zis (.read bytes))]
            (when (pos? len)
              (-> output (.write bytes 0 len))
              (recur (-> zis (.read bytes)))))
          (finally
            (-> output .close)))))))

(defn uncompressed-sources-dir []
  (let [id (jdk/digits-str)]
    (-> "user.home"
        System/getProperty
        (io/file base-prefix "unzipped-jdk-sources" id)
        str)))

(defn unzipped-jdk-source []
  (let [dir (uncompressed-sources-dir)
        file (-> dir io/file (doto .mkdirs))
        lockfile (io/file file ".lock")]
    (locking-file (str lockfile)
                  (fn [_ _]
                    (when (->> file
                               file-seq
                               (remove (some-fn #{file lockfile}
                                                (fn [^File candidate]
                                                  ;; files such as .DS_Store:
                                                  (-> candidate .getName (string/starts-with? ".")))))
                               empty?)
                      (when-let [choice @jdk-sources]
                        (-> file .mkdirs)
                        (uncompress dir choice)))))
    dir))

(defn resources-to-add []
  (cond-> []
    (and @jdk-sources jdk/jdk8?) (conj (unzipped-jdk-source))
    @jdk-sources                 (conj @jdk-sources)))
