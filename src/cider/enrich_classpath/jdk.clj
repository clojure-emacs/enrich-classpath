(ns cider.enrich-classpath.jdk
  (:require
   [clojure.string :as string]))

(defn digits-str []
  (->> "java.version" System/getProperty (re-seq #"\d+") (string/join)))
