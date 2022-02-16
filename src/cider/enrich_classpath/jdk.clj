(ns cider.enrich-classpath.jdk
  (:require
   [clojure.string :as string]))

(defn digits-str []
  (->> "java.version" System/getProperty (re-seq #"\d+") (string/join)))

(defn jdk8? []
  (boolean (re-find #"^1\.8\." (System/getProperty "java.version"))))
