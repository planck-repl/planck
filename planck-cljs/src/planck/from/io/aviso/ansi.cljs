(ns planck.from.io.aviso.ansi
  "Help with generating textual output that includes ANSI escape codes for formatting.
  Ported for use with bootstrap ClojureScript in Planck from
  https://github.com/AvisoNovate/pretty/blob/master/src/io/aviso/ansi.clj"
  (:require-macros
   [planck.from.io.aviso.ansi :refer [generate-color-functions]])
  (:require
   [clojure.string :as str]))

(def ^:const csi
  "The control sequence initiator: `ESC [`"
  "\u001b[")

;; select graphic rendition
(def ^:const sgr
  "The Select Graphic Rendition suffix: m"
  "m")

(def ^:const reset-font
  "Resets the font, clearing bold, italic, color, and background color."
  (str csi sgr))

(generate-color-functions)
