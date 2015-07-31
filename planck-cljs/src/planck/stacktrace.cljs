(ns planck.stacktrace
  (:require [clojure.string :as string]))

(defn source-uri->relative-path
  "Takes a source URI and returns a relative path value suitable for inclusion
  in a canonical stack frame."
  [source-uri]
  {:pre [(string? source-uri)]}
  (let [c (count "file:///")]
    (if (and (> (count source-uri) c)
          (= "file:///" (subs source-uri 0 c)))
      (subs source-uri c)
      (str "<" source-uri ">"))))

(defn stack-line->canonical-frame
  "Parses a stack line into a frame representation, returning nil
  if parse failed."
  [stack-line]
  {:pre  [(string? stack-line)]}
  (let [[function source-uri line column]
        (rest (re-matches #"(.*)@(.*):([0-9]+):([0-9]+)"
                stack-line))]
    (if (and source-uri function line column)
      {:file     (source-uri->relative-path source-uri)
       :function function
       :line     (js/parseInt line)
       :column   (js/parseInt column)}
      (let [[source-uri line column]
            (rest (re-matches #"(.*):([0-9]+):([0-9]+)"
                    stack-line))]
        (if (and source-uri line column)
          {:file     (source-uri->relative-path source-uri)
           :function nil
           :line     (js/parseInt line)
           :column   (js/parseInt column)}
          (when-not (string/blank? stack-line)
            {:file     nil
             :function (string/trim stack-line)
             :line     nil
             :column   nil}))))))

(defn raw-stacktrace->canonical-stacktrace
  "Parse a raw JSC stack representation, parsing it into stack frames.
  The canonical stacktrace must be a vector of maps of the form
  {:file <string> :function <string> :line <integer> :column <integer>}."
  [raw-stacktrace opts]
  {:pre  [(string? raw-stacktrace) (map? opts)]
   :post [(vector? %)]}
  (let [stack-line->canonical-frame (memoize stack-line->canonical-frame)]
    (->> raw-stacktrace
      string/split-lines
      (map stack-line->canonical-frame)
      (remove nil?)
      vec)))

