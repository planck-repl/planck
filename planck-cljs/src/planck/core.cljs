(ns planck.core
  (:require [cljs.spec :as s]
            [planck.repl :as repl])
  (:import [goog.string StringBuffer]))

(s/def ::binding
  (s/cat :name symbol? :value ::s/any))

(s/def ::bindings
  (s/and vector?
         #(even? (count %))
         (s/* ::binding)))

(s/fdef planck.core$macros/with-open
  :args (s/cat :bindings ::bindings :body (s/* ::s/any)))

(def *planck-version* js/PLANCK_VERSION)

(defn exit
  "Causes Planck to terminate with the supplied exit-value."
  [exit-value]
  (js/PLANCK_SET_EXIT_VALUE exit-value)
  (throw (js/Error. "PLANCK_EXIT")))

(s/fdef exit
  :args (s/cat :exit-value integer?))

(defprotocol IClosable
  (-close [this]))

(defprotocol IReader
  "Protocol for reading."
  (-read [this] "Returns available characters as a string or nil if EOF."))

(defprotocol IBufferedReader
  "Protocol for reading line-based content."
  (-read-line [this] "Reads the next line."))

(defrecord Reader [raw-read raw-close]
  IReader
  (-read [_]
    (raw-read))
  IClosable
  (-close [_]
    (raw-close)))

(declare fission!)

(defrecord BufferedReader [raw-read raw-close buffer]
  IReader
  (-read [_]
    (raw-read))
  IBufferedReader
  (-read-line [this]
    (if-let [buffered @buffer]
      (let [n (.indexOf buffered "\n")]
        (if (neg? n)
          (if-let [next-characters (-read this)]
            (do
              (swap! buffer (fn [s] (str s next-characters)))
              (recur this))
            (fission! buffer (fn [s] [nil s])))
          (fission! buffer (fn [s] [(let [residual (subs s (inc n))]
                                      (if (= "" residual)
                                        nil
                                        residual))
                                    (subs s 0 n)]))))
      (when (reset! buffer (-read this))
        (recur this))))
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

(defprotocol IInputStream
  "Protocol for reading binary data."
  (-read-bytes [this] "Returns available bytes as an array of unsigned numbers or nil if EOF."))

(defprotocol IOutputStream
  "Protocol for writing binary data."
  (-write-bytes [this byte-array] "Writes byte array.")
  (-flush-bytes [this] "Flushes output."))

(defrecord InputStream [raw-read-bytes raw-close]
  IInputStream
  (-read-bytes [_]
    (raw-read-bytes))
  IClosable
  (-close [_]
    (raw-close)))

(defrecord OutputStream [raw-write-bytes raw-flush-bytes raw-close]
  IOutputStream
  (-write-bytes [_ byte-array]
    (raw-write-bytes byte-array))
  (-flush-bytes [_]
    (raw-flush-bytes))
  IClosable
  (-close [_]
    (raw-close)))

(defonce
  ^{:doc     "A planck.io/IReader representing standard input for read operations."
    :dynamic true}
  *in*
  (->BufferedReader js/PLANCK_RAW_READ_STDIN nil (atom nil)))

(set! cljs.core/*out* (Writer. js/PLANCK_RAW_WRITE_STDOUT js/PLANCK_RAW_FLUSH_STDOUT nil))

(defonce
  ^{:doc     "A cljs.core/IWriter representing standard error for print operations."
    :dynamic true}
  *err*
  (->Writer js/PLANCK_RAW_WRITE_STDERR js/PLANCK_RAW_FLUSH_STDERR nil))

(defonce
  ^{:doc     "A sequence of the supplied command line arguments, or nil if none were supplied"
    :dynamic true}
  *command-line-args*
  (-> js/PLANCK_INITIAL_COMMAND_LINE_ARGS js->clj seq))

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

(defn read-line
  "Reads the next line from the current value of planck.io/*in*"
  []
  (-read-line *in*))

(s/fdef read-line
  :args (s/cat)
  :ret string?)

(defn line-seq
  "Returns the lines of text from rdr as a lazy sequence of strings.
  rdr must implement IBufferedReader."
  [rdr]
  (when-let [line (-read-line rdr)]
    (cons line (lazy-seq (line-seq rdr)))))

(s/fdef line-seq
  :args (s/cat :rdr #(instance? IBufferedReader %))
  :ret seq?)

(defn read-password
  "Reads the next line from console with echoing disabled.
  It will print out a prompt if supplied"
  ([] (read-password ""))
  ([prompt]
   (js/PLANCK_READ_PASSWORD prompt)))

(s/fdef read-password
  :args (s/cat :prompt (s/? string?))
  :ret string?)

(defonce
  ^{:dynamic true
    :private true}
  *as-file-fn*
  (fn [_]
    (throw (js/Error. "No *as-file-fn* fn set."))))

(defn file-seq
  "A tree seq on files"
  [dir]
  (tree-seq
    (fn [f] (js/PLANCK_IS_DIRECTORY (:path f)))
    (fn [d] (map *as-file-fn*
              (js->clj (js/PLANCK_LIST_FILES (:path d)))))
    (*as-file-fn* dir)))

(s/fdef file-seq
  :args (s/cat :dir :planck.core/coercible-file?)
  :ret? seq?)

(defonce
  ^{:dynamic true
    :private true}
  *reader-fn*
  (fn [_]
    (throw (js/Error. "No *reader-fn* fn set."))))

(defonce
  ^{:dynamic true
    :private true}
  *writer-fn*
  (fn [_]
    (throw (js/Error. "No *writer-fn* fn set."))))

(defn slurp
  "Opens a reader on f and reads all its contents, returning a string.
  See planck.io/reader for a complete list of supported arguments."
  [f & opts]
  (let [r  (apply *reader-fn* f opts)
        sb (StringBuffer.)]
    (try
      (loop [s (-read r)]
        (if (nil? s)
          (.toString sb)
          (do
            (.append sb s)
            (recur (-read r)))))
      (finally
        (-close r)))))

(s/fdef slurp
  :args (s/cat :f :planck.io/coercible-file? :opts (s/* ::s/any))
  :ret string?)

(defn spit
  "Opposite of slurp.  Opens f with writer, writes content, then
  closes f. Options passed to planck.io/writer."
  [f content & opts]
  (let [w (apply *writer-fn* f opts)]
    (try
      (-write w (str content))
      (finally
        (-close w)))))

(s/fdef spit
  :args (s/cat :f :planck.io/coercible-file? :content ::s/any :opts (s/* ::s/any)))

(defn eval
  "Evaluates the form data structure (not text!) and returns the result."
  [form]
  (repl/eval form))

(s/fdef eval
  :args (s/cat :form ::s/any)
  :ret ::s/any)

(defn ns-resolve
  "Returns the var to which a symbol will be resolved in the namespace,
  else nil."
  [ns sym]
  (repl/ns-resolve ns sym))

(s/fdef ns-resolve
  :args (s/cat :ns symbol? :sym symbol?)
  :ret (s/nilable var?))

(defn resolve
  "Returns the var to which a symbol will be resolved in the current
  namespace, else nil."
  [sym]
  (repl/resolve sym))

(s/fdef resolve
  :args (s/cat :sym symbol?)
  :ret (s/nilable var?))

(defn intern
  "Finds or creates a var named by the symbol name in the namespace
  ns (which can be a symbol or a namespace), setting its root binding
  to val if supplied. The namespace must exist. The var will adopt any
  metadata from the name symbol.  Returns the var."
  ([ns name]
   (when-let [the-ns (find-ns (cond-> ns (instance? Namespace ns) ns-name))]
     (repl/eval `(def ~name) (ns-name the-ns))))
  ([ns name val]
   (when-let [the-ns (find-ns (cond-> ns (instance? Namespace ns) ns-name))]
     (repl/eval `(def ~name ~val) (ns-name the-ns)))))

(s/fdef intern
  :args (s/cat :ns (s/or :sym symbol? :ns #(instance? Namespace %))
          :name symbol?
          :val (s/? ::s/any)))

(defn- transfer-ns
  [state ns]
  (-> state
    (assoc-in [:cljs.analyzer/namespaces ns]
      (get-in @repl/st [:cljs.analyzer/namespaces ns]))))

(defn init-empty-state
  "An init function for use with cljs.js/empty-state which initializes
  the empty state with cljs.core analysis metadata.

  This is useful because Planck is built with :dump-core set to false.

  Usage: (cljs.js/empty-state init-empty-state)"
  [state]
  (-> state
    (transfer-ns 'cljs.core)
    (transfer-ns 'cljs.core$macros)))

(s/fdef init-empty-state
  :args (s/cat :state map?)
  :ret map?)

;; Ensure planck.io is loaded so that its facilities are available
(js/goog.require "planck.io")
