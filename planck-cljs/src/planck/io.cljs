(ns planck.io
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

(defprotocol IClosable
  (-close [this]))

(defprotocol IReader
  (-read [this] "Returns available characters as a string or nil of EOF."))

(defrecord Reader [raw-read raw-close]
  IReader
  (-read [_]
    (raw-read))
  IClosable
  (-close [_]
    (raw-close)))

(defrecord Writer [raw-write raw-flush raw-close]
  IWriter
  (-write [_ s]
    (raw-write s))
  (-flush [_]
    (raw-flush))
  IClosable
  (-close [_]
    (raw-close)))

(defonce
  ^{:doc "A planck.io/IReader representing standard input for read operations."
    :dynamic true}
  *in*
  (Reader. js/PLANCK_RAW_READ_STDIN nil))

(set! cljs.core/*out* (Writer. js/PLANCK_RAW_WRITE_STDOUT js/PLANCK_RAW_FLUSH_STDOUT nil))

(defonce
  ^{:doc "A cljs.core/IWriter representing standard error for print operations."
    :dynamic true}
  *err*
  (Writer. js/PLANCK_RAW_WRITE_STDERR js/PLANCK_RAW_FLUSH_STDERR nil))

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
      (Reader.
        (fn [] (.read file-reader))
        (fn [] (.close file-reader)))))
  (make-writer [file opts]
    (let [file-writer (js/PLKFileWriter.openAppend (:path file) (:append opts))]
      (check-utf-8-encoding (:encoding opts))
      (Writer.
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

(defn- fission!
  "Breaks an atom's value into two parts. The supplied function should
  return a pair. The first element will be set to be the atom's new
  value and the second element will be returned."
  [atom f & args]
  (loop []
    (let [old @atom
          [new-in new-out] (apply f old args)]
      (if (compare-and-set! atom old new-in)
        new-out
        (recur)))))

(defonce ^:private buffer (atom nil))

(defn read-line
  "Reads the next line from the current value of planck.io/*in*"
  []
  (if-let [buffered @buffer]
    (let [n (.indexOf buffered "\n")]
      (if (neg? n)
        (if-let [next-characters (-read *in*)]
          (do
            (swap! buffer (fn [s] (str s next-characters)))
            (recur))
          (fission! buffer (fn [s] [nil s])))
        (fission! buffer (fn [s] [(let [residual (subs s (inc n))]
                                    (if (= "" residual)
                                      nil
                                      residual))
                                  (subs s 0 n)]))))
    (when (reset! buffer (-read *in*))
      (recur))))

(defn slurp
  "Slurps a file"
  [filename]
  (or (js/PLANCK_READ_FILE filename)
    (throw (js/Error. filename))))

(defn spit
  "Spits a file"
  [filename content]
  (js/PLANCK_WRITE_FILE filename content)
  nil)

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

