(ns integration.cider.enrich-classpath.jar
  (:require
   [cider.enrich-classpath.jar :as sut]
   [clojure.string :as string]
   [clojure.test :refer [deftest is testing]])
  (:import
   (java.io File)
   (java.util UUID)))

(deftest jar-for!
  (let [jars [(-> (UUID/randomUUID) str),
              (-> (UUID/randomUUID) str)]
        file (File. (sut/jar-for! jars))
        last-modified (-> file .lastModified)]
    (is (-> file .exists)
        "Creates a file")
    (is (= last-modified
           (-> (sut/jar-for! jars)
               File.
               .lastModified))
        "Doesn't re-create the same .jar for equivalent contents")
    (let [v (sut/jar-file->manifest-contents file)]
      (testing v
        (is (re-find #"Manifest-Version: 1.0
Class-Path: [a-z0-9-]+ [a-z0-9-]+
 [a-z0-9-]+
Created-By: mx.cider/enrich-classpath"
                     (string/replace v "\r" ""))
            "Emits a valid Manifest file with two classpath entries in it,
the former being wrapped at column 72, and the latter prefixed by a single space character")))))
