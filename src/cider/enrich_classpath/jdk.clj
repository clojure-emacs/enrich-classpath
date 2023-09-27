(ns cider.enrich-classpath.jdk
  (:require
   [clojure.string :as string]))

(defn digits-str []
  (->> "java.version" System/getProperty (re-seq #"\d+") (string/join)))

(defn jdk8? []
  (boolean (re-find #"^1\.8\." (System/getProperty "java.version"))))

(def javac-tree "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED")
(def javac-code "jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED")

(def javac-tree-opens (str "--add-opens=" javac-tree))
(def javac-code-opens (str "--add-opens=" javac-code))

(def javac-tree-like #{javac-tree javac-tree-opens})
(def javac-code-like #{javac-code javac-code-opens})

(defn maybe-add-opens [xs]
  (cond-> xs
    (and (not (jdk8?))
         (not (some javac-tree-like xs)))

    (conj javac-tree-opens)

    (and (not (jdk8?))
         (not (some javac-code-like xs)))

    (conj javac-code-opens)))
