(ns planck.core
  (:require [planck.repl :as repl])
  (:import [goog.string StringBuffer]))

(def *planck-version* js/PLANCK_VERSION)

(defn exit
  "Causes Planck to terminate with the supplied exit-value."
  [exit-value]
  (js/PLANCK_SET_EXIT_VALUE exit-value)
  (throw (js/Error. "PLANCK_EXIT")))

(defprotocol IClosable
  (-close [this]))

(defprotocol IReader
  "Protocol for reading."
  (-read [this] "Returns available characters as a string or nil if EOF."))

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
  (Reader. js/PLANCK_RAW_READ_STDIN nil))

(set! cljs.core/*out* (Writer. js/PLANCK_RAW_WRITE_STDOUT js/PLANCK_RAW_FLUSH_STDOUT nil))

(defonce
  ^{:doc     "A cljs.core/IWriter representing standard error for print operations."
    :dynamic true}
  *err*
  (Writer. js/PLANCK_RAW_WRITE_STDERR js/PLANCK_RAW_FLUSH_STDERR nil))

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

(defonce
  ^:dynamic
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

(defonce
  ^:dynamic
  *reader-fn*
  (fn [_]
    (throw (js/Error. "No *reader-fn* fn set."))))

(defonce
  ^:dynamic
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

(defn spit
  "Opposite of slurp.  Opens f with writer, writes content, then
  closes f. Options passed to planck.io/writer."
  [f content & opts]
  (let [w (apply *writer-fn* f opts)]
    (try
      (-write w (str content))
      (finally
        (-close w)))))

(defn eval
  "Evaluates the form data structure (not text!) and returns the result."
  [form]
  (repl/eval form))

(defn ns-resolve
  "Returns the var to which a symbol will be resolved in the namespace,
  else nil."
  [ns sym]
  (repl/ns-resolve ns sym))

(defn resolve
  "Returns the var to which a symbol will be resolved in the current
  namespace, else nil."
  [sym]
  (repl/resolve sym))

;; Ensure planck.io is loaded so that its facilities are available
(js/goog.require "planck.io")