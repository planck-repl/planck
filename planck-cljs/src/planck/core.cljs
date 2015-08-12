(ns planck.core)

(defn file-seq
  "A tree seq on PLKFiles"
  [dir]
  (js/PLANCK_IO_FILESEQ dir))
