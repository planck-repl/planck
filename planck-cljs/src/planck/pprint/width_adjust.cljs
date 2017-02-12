(ns planck.pprint.width-adjust
  (:require
    [clojure.string :as string]
    [planck.themes]))

(def plain (planck.themes/get-theme :plain))

(defn make-sample-opts
  [opts]
  (merge opts
    {:theme plain
     :print-length (quot (:width opts) 2)
     :print-level (:width opts)}))

(defn generate-sample
  [pprint x opts width]
  (let [stop-time (+ (system-time) 80)
        sb (js/goog.string.StringBuffer.)]
    (binding [*print-newline* true
              *print-fn* (fn [x]
                           (when (< stop-time (system-time))
                             (throw (ex-info "" {})))
                           (.append sb x))]
      (pprint x (assoc opts :width width)))
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

(defn adjusted-with
  [pprint x opts]
  (let [desired-width (:width opts)
        lower 20
        upper desired-width
        sample-opts (make-sample-opts opts)
        sample-width (fn [trial-width]
                       (text-width (generate-sample pprint x sample-opts trial-width)))
        fits? (fn [trial-width]
                (<= (sample-width trial-width) desired-width))]
    (try
      (bisect lower upper fits?)
      (catch :default _
        desired-width))))

(defn wrap
  [pprint]
  (fn wrapped
    ([x] (wrapped x nil))
    ([x opts] (pprint x (assoc opts :width (adjusted-with pprint x opts))))))
