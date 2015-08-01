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

(defn read-line
  "Reads the next line from the current value of planck.io/*in*"
  []
  ;; todo: Perhaps read from *in* and save charactrs beyond \n in atom?
  ;; what if *in* bound differently between calls, what does Clojure do in that case?
  (-read *in*))
