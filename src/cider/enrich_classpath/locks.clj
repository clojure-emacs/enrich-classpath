(ns cider.enrich-classpath.locks
  (:require
   [clojure.java.io :as io])
  (:import
   (java.io Reader StringWriter Writer)
   (java.nio.channels Channels FileChannel FileLock)
   (java.nio.file Paths StandardOpenOption)))

(defn nonclosing-slurp
  "Like `#'slurp`, but does not close `f`, so that the underlying channel isn't closed either."
  [f]
  (let [sw (StringWriter.)
        ^Reader r (io/reader f)]
    (io/copy r sw)
    (-> sw .toString)))

(defn nonclosing-spit
  "Like `#'spit`, but does not close `f`, so that the underlying channel isn't closed either."
  [f content]
  (let [^Writer w (io/writer f)]
    (-> w (.write (str content)))
    (-> w .flush)))

(def in-process-lock
  "Although Lein invocation concurrency is primarily inter-process, it can also be in-process: https://git.io/JLdS8

  This lock guards against in-process concurrent acquisition of a FileLock,
  which would otherwise throw a `java.nio.channels.OverlappingFileLockException`."
  (Object.))

(defn read! [^FileChannel ch]
  (-> ch (Channels/newReader "UTF-8") nonclosing-slurp))

(defn write! [^FileChannel ch, ^String s]
  (-> ch (Channels/newWriter "UTF-8") (nonclosing-spit s)))

(defn locking-file
  "These file locks guard against concurrent Lein executions, which could otherwise corrupt a given file."
  [^String filename f]
  {:pre [(string? filename)]}
  (locking in-process-lock
    (with-open [c (FileChannel/open (Paths/get filename (into-array String []))
                                    (into-array StandardOpenOption [StandardOpenOption/CREATE
                                                                    StandardOpenOption/READ
                                                                    StandardOpenOption/WRITE
                                                                    StandardOpenOption/SYNC]))
                ;; remember: this lock is closed via with-open
                lock (-> c (.lock 0 Long/MAX_VALUE false))]
      (let [prev-content (read! c)]
        (f prev-content c)))))

(defn read-file! [filename]
  (locking-file filename (fn [v _]
                           v)))

(defn write-file! [filename merge-fn]
  (locking-file filename (fn [v ^FileChannel c]
                           (let [d (merge-fn v)]
                             (when-not (= d v)
                               (-> c (.truncate 0))
                               (write! c d))
                             d))))
