(ns planck.from.io.aviso.ansi
  (:require [clojure.string :as str]))

(defn ^:private def-sgr-const
  "Utility for defining a font-modifying constant."
  [symbol-name color-name & codes]
  `(def ^:const ~(symbol symbol-name)
     ~(str "Constant for ANSI code to enable " color-name " text.")
     (str csi ~(str/join ";" codes) sgr)))

(defn ^:private def-sgr-fn
  "Utility for creating a function that enables some combination of SGR codes around some text, but resets
  the font after the text."
  [fn-name color-name & codes]
  `(defn ~(symbol fn-name)
     ~(str "Wraps the provided text with ANSI codes to render as " color-name " text.")
     [~'text]
     (str (str csi ~(str/join ";" codes) sgr) ~'text (str csi sgr))))

;;; Define functions and constants for each color. The functions accept a string
;;; and wrap it with the ANSI codes to set up a rendition before the text,
;;; and reset the rendition fully back to normal after.
;;; The constants enable the rendition, and require the reset-font value to
;;; return to normal.
;;; For each color C:
;;; - functions:
;;;   - C: change text to that color (e.g., "green")
;;;   - C-bg: change background to that color (e.g., "green-bg")
;;;   - bold-C: change text to bold variation of color (e.g., "bold-green")
;;;   - bold-C-bg: change background to bold variation of color (e.g., "bold-green-bg")
;;; - constants
;;;   - C-font: enable text in that color (e.g., "green-font")
;;;   - C-bg-font: enable background in that color (e.g., "green-bg-font")
;;;   - bold-C-font; enable bold text in that color (e.g., "bold-green-font")
;;;   - bold-C-bg-font; enable background in that bold color (e.g., "bold-green-bg-font")

(defmacro generate-color-functions []
  (let [generate-functions-for-index-color (fn [index color-name]
            [(def-sgr-fn color-name color-name (+ 30 index))
             (def-sgr-fn (str color-name "-bg") (str color-name " background") (+ 40 index))
             (def-sgr-fn (str "bold-" color-name) (str "bold " color-name) 1 (+ 30 index))
             (def-sgr-fn (str "bold-" color-name "-bg") (str "bold " color-name " background") 1 (+ 40 index))
             (def-sgr-const (str color-name "-font") color-name (+ 30 index))
             (def-sgr-const (str color-name "-bg-font") (str color-name " background") (+ 40 index))
             (def-sgr-const (str "bold-" color-name "-font") (str "bold " color-name) 1 (+ 30 index))
             (def-sgr-const (str "bold-" color-name "-bg-font") (str "bold " color-name " background") 1 (+ 40 index))])]
    `(do
       ~@(generate-functions-for-index-color 0 "black")
       ~@(generate-functions-for-index-color 1 "red")
       ~@(generate-functions-for-index-color 2 "green")
       ~@(generate-functions-for-index-color 3 "yellow")
       ~@(generate-functions-for-index-color 4 "blue")
       ~@(generate-functions-for-index-color 5 "magenta")
       ~@(generate-functions-for-index-color 6 "cyan")
       ~@(generate-functions-for-index-color 7 "white"))))

;; ANSI defines quite a few more, but we're limiting to those that display properly in the
;; Cursive REPL.
