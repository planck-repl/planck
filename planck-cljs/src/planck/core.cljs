(ns planck.core)

(defn exit
  "Causes Planck to terminate with the supplied exit-value."
  [exit-value]
  (js/PLANCK_SET_EXIT_VALUE exit-value)
  (throw (js/Error. "PLANCK_EXIT")))

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

(defonce
  ^{:doc "A sequence of the supplied command line arguments, or nil if none were supplied"
    :dynamic true}
  *command-line-args*
  (-> js/PLANCK_INITIAL_COMMAND_LINE_ARGS js->clj seq))

(defn file-seq
  "A tree seq on PLKFiles"
  [dir]
  (js/PLANCK_IO_FILESEQ dir))

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
