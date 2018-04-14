(ns planck.io
  "Planck I/O functionality."
  (:require
   [cljs.spec.alpha :as s]
   [clojure.string :as string]
   [planck.core :refer [with-open]]
   [planck.http :as http]
   [planck.repl :as repl])
  (:import
   (goog Uri)))

(defrecord File [path]
  Object
  (toString [_] path))

(defn- build-uri
  "Builds a URI"
  [scheme server-name server-port uri query-string]
  (doto (Uri.)
    (.setScheme (name (or scheme "http")))
    (.setDomain server-name)
    (.setPort server-port)
    (.setPath uri)
    (.setQuery query-string true)))

(s/fdef build-uri
  :args (s/cat :scheme (s/nilable (s/or :string string? :named #(implements? INamed %)))
          :server-name (s/nilable string?)
          :server-port (s/nilable (s/or :integer integer? :string string?))
          :uri (s/nilable string?)
          :query-string (s/nilable string?))
  :ret #(instance? Uri %))

(defn- has-scheme?
  [uri scheme]
  (= scheme (.getScheme uri)))

(defn- file-uri?
  [uri]
  (has-scheme? uri "file"))

(defn- jar-uri?
  [uri]
  (has-scheme? uri "jar"))

(defn- bundled-uri?
  [uri]
  (has-scheme? uri "bundled"))

(defprotocol Coercions
  "Coerce between various 'resource-namish' things."
  (as-file [x] "Coerce argument to a File.")
  (as-url [x] "Coerce argument to a goog.Uri."))

(extend-protocol Coercions
  nil
  (as-file [_] nil)
  (as-url [_] nil)

  string
  (as-file [s] (File. s))
  (as-url [s] (Uri. s))

  File
  (as-file [f] f)
  (as-url [f] (build-uri :file nil nil (:path f) nil))

  Uri
  (as-url [u] u)
  (as-file [u]
    (if (file-uri? u)
      (as-file (.getPath u))
      (throw (js/Error. (str "Not a file: " u))))))

(defn- as-url-or-file [f]
  (if (string/starts-with? f "http")
    (as-url f)
    (as-file f)))

(defprotocol IOFactory
  "Factory functions that create ready-to-use versions of the various stream
  types, on top of anything that can be unequivocally converted to the
  requested kind of stream.

  Common options include

    :append   true to open stream in append mode
    :encoding  string name of encoding to use, e.g. \"UTF-8\".

  Callers should generally prefer the higher level API provided by reader,
  writer, input-stream, and output-stream."
  (make-reader [x opts] "Creates an IReader. See also IOFactory docs.")
  (make-writer [x opts] "Creates an IWriter. See also IOFactory docs.")
  (make-input-stream [x opts] "Creates an IInputStream. See also IOFactory docs.")
  (make-output-stream [x opts] "Creates an IOutputStream. See also IOFactory docs."))

(defonce ^:private open-file-reader-descriptors (atom #{}))
(defonce ^:private open-file-writer-descriptors (atom #{}))
(defonce ^:private open-file-input-stream-descriptors (atom #{}))
(defonce ^:private open-file-output-stream-descriptors (atom #{}))

(defn- bad-file-descriptor?
  [file-descriptor]
  (= "0" file-descriptor))

(defn- check-file-descriptor
  [file-descriptor file opts]
  (when (bad-file-descriptor? file-descriptor)
    (throw (ex-info "Failed to open file." {:file file, :opts opts}))))

(defn- make-jar-uri-reader
  [jar-uri opts]
  (let [file-uri (Uri. (.getPath jar-uri))]
    (if (file-uri? file-uri)
      (let [[file-path resource] (string/split (.getPath file-uri) #"!/")
            [content error-msg] (js/PLANCK_LOAD_FROM_JAR file-path resource)]
        (if-not (nil? content)
          (planck.core/make-string-reader content)
          (throw (ex-info (str "Failed to extract resource from JAR: " error-msg)
                   {:uri       jar-uri
                    :jar-file  file-path
                    :resource  resource
                    :error-msg error-msg}))))
      (throw (ex-info "Not a JAR file URI"
               {:uri     jar-uri
                :sub-uri file-uri})))))

(defn- make-bundled-uri-reader
  [bundle-uri opts]
  (let [path    (.getPath bundle-uri)
        content (first (js/PLANCK_LOAD path))]
    (planck.core/make-string-reader content)))

(defn- make-http-uri-reader
  [uri opts]
  (planck.core/make-string-reader (:body (http/get (str uri) {}))))

(defn- make-http-uri-writer
  [uri opts]
  (planck.core/->Writer
    (fn [content]
      (let [name     (or (:param-name opts) "file")
            filename (or (:filename opts) "file.pnk")]
        (http/post (str uri) {:multipart-params [[name [content filename]]]}))
      nil)
    (fn [])
    (fn [])))

(extend-protocol IOFactory
  string
  (make-reader [s opts]
    (make-reader (as-url-or-file s) opts))
  (make-writer [s opts]
    (make-writer (as-url-or-file s) opts))
  (make-input-stream [s opts]
    (make-input-stream (as-file s) opts))
  (make-output-stream [s opts]
    (make-output-stream (as-file s) opts))

  File
  (make-reader [file opts]
    (let [file-descriptor (js/PLANCK_FILE_READER_OPEN (:path file) (:encoding opts))]
      (check-file-descriptor file-descriptor file opts)
      (swap! open-file-reader-descriptors conj file-descriptor)
      (planck.core/->Reader
        (fn []
          (if (contains? @open-file-reader-descriptors file-descriptor)
            (let [[result err] (js/PLANCK_FILE_READER_READ file-descriptor)]
              (if err
                (throw (js/Error. err)))
              result)
            (throw (js/Error. "File closed."))))
        (fn []
          (when (contains? @open-file-reader-descriptors file-descriptor)
            (swap! open-file-reader-descriptors disj file-descriptor)
            (js/PLANCK_FILE_READER_CLOSE file-descriptor)))
        (atom nil)
        (atom 0))))
  (make-writer [file opts]
    (let [file-descriptor (js/PLANCK_FILE_WRITER_OPEN (:path file) (boolean (:append opts)) (:encoding opts))]
      (check-file-descriptor file-descriptor file opts)
      (swap! open-file-writer-descriptors conj file-descriptor)
      (planck.core/->Writer
        (fn [s]
          (if (contains? @open-file-writer-descriptors file-descriptor)
            (if-let [err (js/PLANCK_FILE_WRITER_WRITE file-descriptor s)]
              (throw (js/Error. err)))
            (throw (js/Error. "File closed.")))
          nil)
        (fn []
          (if (contains? @open-file-writer-descriptors file-descriptor)
            (if-let [err (js/PLANCK_FILE_WRITER_FLUSH file-descriptor)]
              (throw (js/Error. err)))
            (throw (js/Error. "File closed.")))
          nil)
        (fn []
          (when (contains? @open-file-writer-descriptors file-descriptor)
            (swap! open-file-writer-descriptors disj file-descriptor)
            (js/PLANCK_FILE_WRITER_CLOSE file-descriptor))))))
  (make-input-stream [file opts]
    (let [file-descriptor (js/PLANCK_FILE_INPUT_STREAM_OPEN (:path file))]
      (check-file-descriptor file-descriptor file opts)
      (swap! open-file-input-stream-descriptors conj file-descriptor)
      (planck.core/->InputStream
        (fn []
          (if (contains? @open-file-input-stream-descriptors file-descriptor)
            (js->clj (js/PLANCK_FILE_INPUT_STREAM_READ file-descriptor))
            (throw (js/Error. "File closed."))))
        (fn []
          (when (contains? @open-file-input-stream-descriptors file-descriptor)
            (swap! open-file-input-stream-descriptors disj file-descriptor)
            (js/PLANCK_FILE_INPUT_STREAM_CLOSE file-descriptor))))))
  (make-output-stream [file opts]
    (let [file-descriptor (js/PLANCK_FILE_OUTPUT_STREAM_OPEN (:path file) (boolean (:append opts)))]
      (check-file-descriptor file-descriptor file opts)
      (swap! open-file-output-stream-descriptors conj file-descriptor)
      (planck.core/->OutputStream
        (fn [byte-array]
          (if (contains? @open-file-output-stream-descriptors file-descriptor)
            (js/PLANCK_FILE_OUTPUT_STREAM_WRITE file-descriptor (clj->js byte-array))
            (throw (js/Error. "File closed."))))
        (fn []
          (if (contains? @open-file-output-stream-descriptors file-descriptor)
            (js/PLANCK_FILE_OUTPUT_STREAM_FLUSH file-descriptor)
            (throw (js/Error. "File closed."))))
        (fn []
          (when (contains? @open-file-output-stream-descriptors file-descriptor)
            (swap! open-file-output-stream-descriptors disj file-descriptor)
            (js/PLANCK_FILE_OUTPUT_STREAM_CLOSE file-descriptor))))))

  Uri
  (make-reader [uri opts]
    (cond
      (file-uri? uri) (make-reader (as-file uri) opts)
      (jar-uri? uri) (make-jar-uri-reader uri opts)
      (bundled-uri? uri) (make-bundled-uri-reader uri opts)
      :else (make-http-uri-reader uri opts)))
  (make-writer [uri opts]
    (cond
      (file-uri? uri) (make-writer (as-file uri) opts)
      (jar-uri? uri) (throw (ex-info "Can't write to jar URI" {:uri uri}))
      (bundled-uri? uri) (throw (ex-info "Can't write to bundled URI" {:uri uri}))
      :else (make-http-uri-writer uri opts)))

  default
  (make-reader [x _]
    (if (satisfies? planck.core/IReader x)
      x
      (throw (ex-info (str "Can't make a reader from " x) {}))))
  (make-writer [x _] nil
    (if (satisfies? IWriter x)
      x
      (throw (ex-info (str "Can't make a writer from " x) {}))))
  (make-input-stream [x _]
    (if (satisfies? planck.core/IInputStream x)
      x
      (throw (ex-info (str "Can't make an input stream from " x) {}))))
  (make-output-stream [x _]
    (if (satisfies? planck.core/IOutputStream x)
      x
      (throw (ex-info (str "Can't make an output stream from " x) {})))))

(defn reader
  "Attempts to coerce its argument into an open IPushbackReader."
  [x & opts]
  (make-reader x (when opts (apply hash-map opts))))

(defn writer
  "Attempts to coerce its argument into an open IWriter."
  [x & opts]
  (make-writer x (when opts (apply hash-map opts))))

(defn input-stream
  "Attempts to coerce its argument into an open IInputStream."
  [x & opts]
  (make-input-stream x (when opts (apply hash-map opts))))

(defn output-stream
  "Attempts to coerce its argument into an open IOutputStream."
  [x & opts]
  (make-output-stream x (when opts (apply hash-map opts))))

(def ^:private ^:const path-separator "/")

(defn as-relative-path
  "Take an as-file-able thing and return a string if it is
   a relative path, else throws an exception."
  [x]
  (let [f (as-file x)]
    (if (string/starts-with? (:path f) path-separator)
      (throw (ex-info (str f " is not a relative path") {:f f}))
      (:path f))))

(s/fdef as-relative-path
  :args (s/cat :x any?)
  :ret string?)

(defn ^boolean file?
  "Returns true if x is a File."
  [x]
  (instance? File x))

(s/fdef file?
  :args (s/cat :x any?)
  :ret boolean?)

(defn file
  "Returns a File, passing each arg to as-file. Multiple-arg versions treat
  the first argument as parent and subsequent args as children relative to the
  parent."
  ([arg]
   (as-file arg))
  ([parent child]
   (File. (str (:path (as-file parent)) path-separator (as-relative-path child))))
  ([parent child & more]
   (reduce file (file parent child) more)))

(s/fdef file
  :args (s/cat :path-or-parent any? :more (s/* any?))
  :ret file?)

(defn file-attributes
  "Returns a map containing the attributes of the item at a given path."
  [path]
  (some-> path
    as-file
    :path
    js/PLANCK_FSTAT
    (js->clj :keywordize-keys true)
    (update-in [:type] keyword)
    (update-in [:created] #(js/Date. %))
    (update-in [:modified] #(js/Date. %))))

(s/fdef file-attributes
  :args (s/cat :path (s/nilable (s/or :string string? :file file?)))
  :ret map?)

(defn delete-file
  "Delete file f."
  [f]
  (js/PLANCK_DELETE (:path (as-file f))))

(s/fdef delete-file
  :args (s/cat :f (s/or :string string? :file file?)))

(defn ^boolean directory?
  "Checks if dir is a directory."
  [dir]
  (js/PLANCK_IS_DIRECTORY (:path (as-file dir))))

(s/fdef directory?
  :args (s/cat :dir (s/or :string string? :file file?))
  :ret boolean?)

(defn resource
  "Returns the URI for a named resource."
  [n]
  (when-some [[_ _ loaded-path loaded-type loaded-location] (js/PLANCK_LOAD n)]
    (case loaded-type
      "jar" (Uri. (str "jar:file:" loaded-location "!/" loaded-path))
      "src" (build-uri "file" "" nil loaded-path nil)
      "bundled" (build-uri "bundled" nil nil loaded-path nil))))

(s/fdef resource
  :args (s/cat :n (s/nilable string?))
  :ret (s/nilable #(instance? Uri %)))

(defn- get-parent-file [file]
  (let [path (:path file)]
    (let [ndx (.lastIndexOf path "/")]
      (if (< ndx 1)
        (if (> (count path) 1)
          (planck.io/file path-separator)
          nil)
        (planck.io/file (subs path 0 ndx))))))

(defn make-parents
  "Given the same arg(s) as for file, creates all parent directories of
  the file they represent."
  [f & more]
  (when-some [parent (get-parent-file (apply file f more))]
    (js/PLANCK_MKDIRS (:path parent))))

(s/fdef make-parents
  :args (s/cat :path-or-parent any? :more (s/* any?))
  :ret boolean?)

(defmulti
  ^{:doc "Internal helper for copy"
    :private true
    :arglists '([input output opts])}
  do-copy
  (fn [input output opts] [(type input) (type output)]))

(defmethod do-copy [planck.core/InputStream planck.core/OutputStream] [input output opts]
  (loop []
    (when-some [byte-array (planck.core/-read-bytes input)]
      (do
        (planck.core/-write-bytes output byte-array)
        (recur)))))

(defmethod do-copy [planck.core/InputStream planck.core/Writer] [input output opts]
  (let [bytes      (->> (repeatedly #(planck.core/-read-bytes input))
                     (take-while some?)
                     (map vec)
                     (apply concat))
        utf8->str  (comp js/decodeURIComponent js/escape)
        codes->str (fn [coll] (apply str (map char coll)))]
    (do-copy (-> bytes codes->str utf8->str) output)) nil)

(defmethod do-copy [planck.core/InputStream File] [input output opts]
  (with-open [out (output-stream output)]
    (do-copy input out nil)))

(defmethod do-copy [planck.core/Reader planck.core/OutputStream] [input output opts]
  (do-copy (planck.core/slurp input) output))

(defmethod do-copy [planck.core/Reader planck.core/Writer] [input output opts]
  (loop []
    (when-some [s (planck.core/-read input)]
      (do
        (-write output s)
        (recur)))))

(defmethod do-copy [planck.core/Reader File] [input output opts]
  (with-open [out (writer output)]
    (do-copy input out nil)))

(defmethod do-copy [File planck.core/OutputStream] [input output opts]
  (with-open [in (input-stream input)]
    (do-copy in output)))

(defmethod do-copy [File planck.core/Writer] [input output opts]
  (with-open [in (reader input)]
    (do-copy in output nil)))

(defmethod do-copy [File File] [input output opts]
  (js/PLANCK_COPY (:path input) (:path output)))

(defmethod do-copy [js/String planck.core/OutputStream] [input output opts]
  (let [str->utf8 (comp js/unescape js/encodeURIComponent)
        str->chars (fn [s] (map #(.charCodeAt %) s))]
    (planck.core/-write-bytes output (-> input str->utf8 str->chars to-array))))

(defmethod do-copy [js/String planck.core/Writer] [input output opts]
  (do-copy (planck.core/make-string-reader input) output nil))

(defmethod do-copy [js/String File] [input output opts]
  (with-open [out (writer output)]
    (do-copy (planck.core/make-string-reader input) out)))

(defn copy
  "Copies input to output. Returns nil or throws an exception.

  Input may be an IInputStream or IReader created using planck.io, File, or
  string.

  Output may be an IOutputStream or IWriter created using planck.io, or File.

  The opts arg is included for compatibility with clojure.java.io/copy
  but ignored. If translating between char and byte representations, UTF-8
  encoding is assumed.

  Does not close any streams except those it opens itself (on a File)."
  [input output & opts]
  (do-copy input output (when opts (apply hash-map opts))))

(s/fdef copy
  :args (s/cat :input any? :output any? :opts (s/* any?))
  :ret nil?)

;; These have been moved
(def ^:deprecated read-line planck.core/read-line)
(def ^:deprecated slurp planck.core/slurp)
(def ^:deprecated spit planck.core/spit)

(set! planck.core/*reader-fn* reader)
(set! planck.core/*writer-fn* writer)
(set! planck.core/*as-file-fn* as-file)
(set! planck.core/*file?-fn* file?)
