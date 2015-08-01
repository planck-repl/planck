(ns planck.io)

(defprotocol IReader
  (-read [this] "Returns available characters as a string or nil of EOF."))

(defrecord Reader [raw-read raw-close]
  IReader
  (-read [_]
    (raw-read)))

(defrecord Writer [raw-write raw-flush]
  IWriter
  (-write [_ s]
    (raw-write s))
  (-flush [_]
    (raw-flush)))

(defonce
  ^{:doc "A planck.io/IReader representing standard input for read operations."
    :dynamic true}
  *in*
  (Reader. js/PLANCK_RAW_READ_STDIN nil))

(set! cljs.core/*out* (Writer. js/PLANCK_RAW_WRITE_STDOUT js/PLANCK_RAW_FLUSH_STDOUT))

(defonce
  ^{:doc "A cljs.core/IWriter representing standard error for print operations."
    :dynamic true}
  *err*
  (Writer. js/PLANCK_RAW_WRITE_STDERR js/PLANCK_RAW_FLUSH_STDERR))

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

(defn- fission! [atom f & args]
  "Breaks an atom's value into two parts. The supplied function should
  return a pair. The first element will be set to be the atom's new
  value and the second element will be returned."
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