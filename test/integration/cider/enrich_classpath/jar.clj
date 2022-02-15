(ns integration.cider.enrich-classpath.jar
  (:require
   [cider.enrich-classpath.jar :as sut]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.test :refer [deftest is testing]])
  (:import
   (java.io ByteArrayOutputStream File)
   (java.util UUID)
   (java.util.jar JarInputStream)))

(deftest jar-for!
  (let [jars [(-> (UUID/randomUUID) str),
              (-> (UUID/randomUUID) str)]
        filename (sut/jar-for! jars)
        ^File file (File. filename)
        last-modified (-> file .lastModified)]
    (is (-> file .exists)
        "Creates a file")
    (is (= last-modified
           (-> (sut/jar-for! jars)
               File.
               .lastModified))
        "Doesn't re-create the same .jar for equivalent contents")
    (let [os (ByteArrayOutputStream.)
          _ (-> file io/input-stream JarInputStream. .getManifest (.write os))
          v (-> os .toByteArray (String. "UTF-8"))]
      (testing v
        (is (re-find #"Manifest-Version: 1.0
Class-Path: [a-z0-9-]+ [a-z0-9-]+
 [a-z0-9-]+
Created-By: mx.cider/enrich-classpath"
                     (string/replace v "\r" ""))
            "Emits a valid Manifest file with two classpath entries of in it,
the former being wrapped at column 72, and the latter prefixed by a single space character")))))
