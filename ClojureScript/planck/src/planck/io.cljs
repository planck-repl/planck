(ns planck.io)

(defn slurp
  "Slurps a file"
  [filename]
  (or (js/PLANCK_SLURP_FN filename)
    (throw (js/Error. filename))))

(defn spit
  "Spits a file"
  [filename content]
  (js/PLANCK_SPIT_FN filename content)
  nil)