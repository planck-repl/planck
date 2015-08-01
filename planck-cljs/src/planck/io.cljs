(ns planck.io)

(defonce
  ^{:doc "Represents standard input for read operations."
    :dynamic true}
  *in*
  nil)

(defonce
  ^{:doc "Represents standard output for print operations."
    :dynamic true}
  *out*
  nil)

(defonce
  ^{:doc "Represents standard error for print operations."
    :dynamic true}
  *err*
  nil)

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

(defn readline
  "Reads a line from standard in"
  []
  (js/PLANCK_READ_LINE))
