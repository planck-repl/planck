(ns planck.io
  (:require planck.core)
  #_(:import goog.Uri))

(defrecord File [path])

#_(defn build-uri
  "Builds a URI"
  [scheme server-name server-port uri query-string]
  (doto (Uri.)
    (.setScheme (name (or scheme "http")))
    (.setDomain server-name)
    (.setPort server-port)
    (.setPath uri)
    (.setQuery query-string true)))

(defprotocol Coercions
  "Coerce between various 'resource-namish' things."
  (as-file [x] "Coerce argument to a File.")
  #_(as-url [x] "Coerce argument to a goog.Uri."))

(extend-protocol Coercions
  nil
  (as-file [_] nil)
  #_(as-url [_] nil)

  string
  (as-file [s] (File. s))
  #_(as-url [s] (Uri. s))

  File
  (as-file [f] f)
  #_(as-url [f] (build-uri :file nil nil (:path f) nil))

  #_js/goog.Uri
  #_(as-url [u] u)
  #_(as-file [u]
    (if (= "file" (.getScheme u))
      (as-file (.getPath u))
      (throw (js/Error. (str "Not a file: " u))))))

(defprotocol IOFactory
  "Factory functions that create ready-to-use versions of
  the various stream types, on top of anything that can
  be unequivocally converted to the requested kind of stream.

  Common options include

    :append   true to open stream in append mode
    :encoding  string name of encoding to use, e.g. \"UTF-8\".

    Callers should generally prefer the higher level API provided by
    reader, writer, input-stream, and output-stream."
  (make-reader [x opts] "Creates an IReader. See also IOFactory docs.")
  (make-writer [x opts] "Creates an IWriter. See also IOFactory docs.")
  #_(make-input-stream [x opts] "Creates an IInputStream. See also IOFactory docs.")
  #_(make-output-stream [x opts] "Creates an IOutputStream. See also IOFactory docs."))

(defn- check-utf-8-encoding [encoding]
  (when (and encoding (not= encoding "UTF-8"))
    (throw js/Error. (str "Unsupported encoding: " encoding))))

(extend-protocol IOFactory
  string
  (make-reader [s opts]
    (make-reader (as-file s) opts))
  (make-writer [s opts]
    (make-writer (as-file s) opts))

  File
  (make-reader [file opts]
    (let [file-reader (js/PLKFileReader.open (:path file))]
      (check-utf-8-encoding (:encoding opts))
      (planck.core/Reader.
        (fn [] (.read file-reader))
        (fn [] (.close file-reader)))))
  (make-writer [file opts]
    (let [file-writer (js/PLKFileWriter.openAppend (:path file) (:append opts))]
      (check-utf-8-encoding (:encoding opts))
      (planck.core/Writer.
        (fn [s] (.write file-writer s))
        (fn [] (.flush file-writer))
        (fn [] (.close file-writer))))))

(defn reader
  "Attempts to coerce its argument into an open IReader."
  [x & opts]
  (make-reader x (when opts (apply hash-map opts))))

(defn writer
  "Attempts to coerce its argument into an open IWriter."
  [x & opts]
  (make-writer x (when opts (apply hash-map opts))))

(defn file
  "Returns a PLKFile, passing each arg to as-file.  Multiple-arg
   versions treat the first argument as parent and subsequent args as
   children relative to the parent."
  ([arg]                      
    (js/PLANCK_IO_FILE arg))
  ([parent & more]
     (js/PLANCK_IO_FILE (apply str parent more))))

(defn delete-file
  "Delete file f."
  [f]
  (.deleteFile f))

;; These have been moved
(def ^:deprecated read-line planck.core/read-line)
(def ^:deprecated slurp planck.core/slurp)
(def ^:deprecated spit planck.core/spit)

