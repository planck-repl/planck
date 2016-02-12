(ns planck.themes
  (:require [planck.from.io.aviso.ansi :as ansi]))

(def ^:private colorize-fn-dumb identity)
(def ^:private colorize-off-dumb "")

(def ^:private dumb
  {:results-fn   colorize-fn-dumb
   :ex-msg-fn    colorize-fn-dumb
   :ex-stack-fn  colorize-fn-dumb
   :err-font     colorize-off-dumb
   :verbose-font colorize-off-dumb
   :reset-font   colorize-off-dumb})

(def ^:private theme-ansi-base
  {:reset-font ansi/reset-font})

(def ^:private light
  (merge theme-ansi-base
    {:results-fn   ansi/blue
     :ex-msg-fn    ansi/bold-red
     :ex-stack-fn  ansi/green
     :err-font     ansi/red-font
     :verbose-font ansi/white-font}))

(def ^:private dark
  (merge theme-ansi-base
    {:results-fn   ansi/green
     :ex-msg-fn    ansi/bold-red
     :ex-stack-fn  ansi/yellow
     :err-font     ansi/red-font
     :verbose-font ansi/white-font}))

(def ^:private themes
  {:plain dumb
   :light light
   :dark  dark})

(defn get-theme
  [theme-id]
  (get themes theme-id dumb))
