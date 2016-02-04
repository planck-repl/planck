(ns planck.ansi
  "Help with generating textual output that includes ANSI escape codes for formatting.
  Ported for use with bootstrap ClojureScript in Planck from
  https://github.com/AvisoNovate/pretty/blob/master/src/io/aviso/ansi.clj"
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

(defn ^:private def-sgr-const
  "Utility for defining a font-modifying constant."
  [eval symbol-name color-name & codes]
  (eval
    `(def ^:const ~(symbol symbol-name)
       ~(str "Constant for ANSI code to enable " color-name " text.")
       (str csi ~(str/join ";" codes) sgr))))

(defn ^:private def-sgr-fn
  "Utility for creating a function that enables some combination of SGR codes around some text, but resets
  the font after the text."
  [eval fn-name color-name & codes]
  (eval
    `(defn ~(symbol fn-name)
       ~(str "Wraps the provided text with ANSI codes to render as " color-name " text.")
       [~'text]
       (str (str csi ~(str/join ";" codes) sgr) ~'text (str csi sgr)))))

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

(defn- init-color-fns-and-constants
  [eval]
  (doall
    (map-indexed (fn [index color-name]
                   (def-sgr-fn eval color-name color-name (+ 30 index))
                   (def-sgr-fn eval (str color-name "-bg") (str color-name " background") (+ 40 index))
                   (def-sgr-fn eval (str "bold-" color-name) (str "bold " color-name) 1 (+ 30 index))
                   (def-sgr-fn eval (str "bold-" color-name "-bg") (str "bold " color-name " background") 1 (+ 40 index))
                   (def-sgr-const eval (str color-name "-font") color-name (+ 30 index))
                   (def-sgr-const eval (str color-name "-bg-font") (str color-name " background") (+ 40 index))
                   (def-sgr-const eval (str "bold-" color-name "-font") (str "bold " color-name) 1 (+ 30 index))
                   (def-sgr-const eval (str "bold-" color-name "-bg-font") (str "bold " color-name " background") 1 (+ 40 index)))
      ["black" "red" "green" "yellow" "blue" "magenta" "cyan" "white"])))

;; ANSI defines quite a few more, but we're limiting to those that display properly in the
;; Cursive REPL.

(defn- init-font-fns-and-constants
  [eval]
  (doseq [[font-name code] [['bold 1]
                            ['italic 3]
                            ['inverse 7]]]
    (def-sgr-fn eval font-name font-name code)
    (def-sgr-const eval (str font-name "-font") font-name code)))

(defn init
  [eval]
  (init-color-fns-and-constants eval)
  (init-font-fns-and-constants eval))

(comment
  (def ^:const ^:private ansi-pattern #"")

  (defn strip-ansi
    "Removes ANSI codes from a string, returning just the raw text."
    [string]
    (str/replace string ansi-pattern ""))

  (defn visual-length
    "Returns the length of the string, with ANSI codes stripped out."
    [string]
    (-> string strip-ansi .-length)))
