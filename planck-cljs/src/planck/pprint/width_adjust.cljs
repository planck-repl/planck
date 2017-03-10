(ns planck.pprint.width-adjust
  "Adjust pretty-print width for trailing delimiters."
  (:require
   [clojure.string :as string]
   [fipp.engine :refer [annotate-begins annotate-rights format-nodes serialize]]
   [planck.themes]))

(def plain (planck.themes/get-theme :plain))

(defn pprint-document [print-fn document options]
  (->> (serialize document)
    (eduction
      annotate-rights
      (annotate-begins options)
      (format-nodes options))
    (run! print-fn)))

(defn counting-print
  [print-fn max-prints]
  (let [counter (atom 0)]
    (fn [x]
      (print-fn x)
      (when (== max-prints (swap! counter inc))
        (throw (ex-info "" {::count-reached true}))))))

(defn count-reached?
  [e]
  (::count-reached (ex-data e)))

(defn generate-sample
  [pprint x opts width max-prints]
  (let [sb (js/goog.string.StringBuffer.)
        print-to-sb (fn [x]
                      (.append sb x))]
    (try
      (pprint x (assoc opts :width width
                            :pprint-document (partial pprint-document (counting-print print-to-sb max-prints))))
      (catch :default e
        (when-not (count-reached? e)
          (throw e))))
    (str sb)))

(defn text-width
  [text]
  (->> text
    string/split-lines
    (map count)
    (apply max)))

(defn bisect
  [lower upper good?]
  (if (or (good? upper)
          (<= upper lower)
          (not (good? lower)))
    upper
    (loop [lower lower
           upper upper]
      (let [mid (quot (+ lower upper) 2)]
        (if (or (== mid upper)
                (== mid lower))
          lower
          (if (good? mid)
            (recur mid upper)
            (recur lower mid)))))))

(defn force-eval
  [pprint x opts max-prints]
  (try
    (pprint x (assoc opts :pprint-document (partial pprint-document (counting-print identity max-prints))))
    false
    (catch :default e
      (if (count-reached? e)
        true
        (throw e)))))

(defn adjusted-with
  [pprint x opts]
  (let [plain-opts (assoc opts :theme plain)
        max-prints 1024]
    ;; We first force the evaluation of up to max-prints to ensure any print
    ;; side effects occur. We give up if max-prints is reached.
    (if (force-eval pprint x plain-opts max-prints)
      (:width opts)
      (let [desired-width (:width opts)
            lower 20
            upper desired-width
            sample-width (fn [trial-width]
                           (text-width (generate-sample pprint x plain-opts trial-width max-prints)))
            fits? (fn [trial-width]
                    (<= (sample-width trial-width) desired-width))]
        (bisect lower upper fits?)))))

(defn wrap
  [pprint]
  (fn wrapped
    ([x] (wrapped x nil))
    ([x opts] (pprint x (assoc opts :width (adjusted-with pprint x opts))))))
