(ns cider.enrich-classpath.source-analysis
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string])
  (:import
   (java.io File)
   (java.util.zip ZipException ZipEntry ZipInputStream)))

(defmacro while-let
  {:style/indent 1}
  [[sym expr] & body]
  `(loop [~sym ~expr]
     (when ~sym
       ~@body
       (recur ~expr))))

(defn ls-zip [target]
  (let [v (volatile! [])
        zis (-> target io/input-stream ZipInputStream.)]
    (try
      (while-let [entry (-> zis .getNextEntry)]
        (vswap! v conj (-> ^ZipEntry entry .getName (string/split #"/"))))
      (catch ZipException _)
      (finally
        (-> zis .close)))
    @v))

(def factory-files
  "https://github.com/clojure-emacs/enrich-classpath/issues/27"
  #{"javax.xml.bind.JAXBContext"
    "javax.xml.datatype.DatatypeFactory"
    "javax.xml.parsers.DocumentBuilderFactory"
    "javax.xml.parsers.SAXParserFactory"
    "javax.xml.soap.MessageFactory"
    "javax.xml.soap.SOAPConnectionFactory"
    "javax.xml.soap.SOAPFactory"
    "javax.xml.stream.XMLEventFactory"
    "javax.xml.stream.XMLInputFactory"
    "javax.xml.stream.XMLOutputFactory"
    "javax.xml.transform.TransformerFactory"
    "javax.xml.validation.SchemaFactory"
    "javax.xml.ws.Service"
    "javax.xml.xpath.XPathFactory"
    "org.apache.xerces.xni.parser.XMLParserConfiguration"
    "org.w3c.dom.DOMImplementationSourceList"
    "org.xml.sax.XMLReader"
    "org.xml.sax.Driver"
    "org.xml.sax.helpers.XMLReaderFactory"})

(def factory-file-like-re
  #"^([a-z\d]+\.)+[A-Z]")

(defn bad-source? [[id version _classifier-keyword classifier]]
  {:pre [(symbol? id)
         (string? version)
         (keyword? _classifier-keyword)
         (string? classifier)]}
  (when (#{"sources"} classifier)
    (let [[groupid artifactid :as x] (-> id str (string/split #"/"))
          artifactid (or artifactid groupid) ;; concise notation
          _ (assert artifactid x)
          segments (-> groupid (string/split #"\."))
          home (System/getProperty "user.home")
          file (apply io/file home ".m2" "repository" segments)
          artifact (str artifactid "-" version "-" classifier  ".jar")
          ^File file (io/file file artifactid version artifact)]
      (when (-> file .exists)
        (let [contained-files (ls-zip file)]
          (boolean (or (not-any? (fn [fragments]
                                   (let [^String s (last fragments)]
                                     (or (-> s (.endsWith ".java"))
                                         (-> s (.endsWith ".scala")))))
                                 contained-files)
                       (some (fn [[dir :as fragments]]
                               (let [filename (peek fragments)]
                                 (or (contains? factory-files filename)
                                     (re-find factory-file-like-re filename))))
                             contained-files))))))))
