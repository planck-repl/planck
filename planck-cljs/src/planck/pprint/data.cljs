(ns planck.pprint.data
  (:require [fipp.visit :refer [visit visit*]]
            [fipp.engine :refer (pprint-document)]
            [planck.themes]))

;; Derived from fipp.edn

(defn pretty-coll [{:keys [print-level print-length] :as printer}
                   open xs sep close f]
  (let [printer  (cond-> printer print-level (update :print-level dec))
        xform    (comp (if print-length (take print-length) identity)
                   (map #(f printer %))
                   (interpose sep))
        ys       (if (pos? (or print-level 1))
                   (sequence xform xs)
                   "#")
        ellipsis (when (and print-length (seq (drop print-length xs)))
                   [:span sep "..."])]
    [:group open [:align ys ellipsis] close]))


(defn wrap-theme
  [kw theme text]
  [:span [:pass (kw theme)] [:text text] [:pass (:reset-font theme)]])

(defrecord PlanckPrinter [symbols print-meta print-length print-level theme keyword-ns]

  fipp.visit/IVisitor

  (visit-unknown [this x]
    [:text (pr-str x)])


  (visit-nil [this]
    (wrap-theme :results-font theme "nil"))

  (visit-boolean [this x]
    (wrap-theme :results-font theme (str x)))

  (visit-string [this x]
    (wrap-theme :results-string-font theme (pr-str x)))

  (visit-character [this x]
    (wrap-theme :results-string-font theme (pr-str x)))

  (visit-symbol [this x]
    [:text (str x)])

  (visit-keyword [this x]
    (wrap-theme :results-keyword-font theme
      (if (and keyword-ns
               (= (namespace x) (str keyword-ns)))
        (str "::" (name x))
        (pr-str x))))

  (visit-number [this x]
    (wrap-theme :results-font theme (pr-str x)))

  (visit-seq [this x]
    (if-let [pretty (symbols (first x))]
      (pretty this x)
      (pretty-coll this "(" x :line ")" visit)))

  (visit-vector [this x]
    (pretty-coll this "[" x :line "]" visit))

  (visit-map [this x]
    (pretty-coll this "{" x [:span "," :line] "}"
      (fn [printer [k v]]
        [:span (visit printer k) " " (visit printer v)])))

  (visit-set [this x]
    (pretty-coll this "#{" x :line "}" visit))

  (visit-tagged [this {:keys [tag form]}]
    [:group "#" (pr-str tag)
     (when (or (and print-meta (meta form))
               (not (coll? form)))
       " ")
     (visit this form)])


  (visit-meta [this m x]
    (if print-meta
      [:align [:span "^" (visit this m)] :line (visit* this x)]
      (visit* this x)))

  (visit-var [this x]
    [:text (pr-str x)])

  (visit-pattern [this x]
    [:text (pr-str x)])

  (visit-record [this x]
    [:text (pr-str x)]))

(defn pprint
  ([x] (pprint x {}))
  ([x options]
   (let [defaults  {:symbols      {}
                    :print-length *print-length*
                    :print-level  *print-level*
                    :print-meta   *print-meta*
                    :theme        planck.themes/dumb}
         full-opts (merge defaults options)
         printer   (map->PlanckPrinter full-opts)]
     (pprint-document (visit printer x) full-opts))))
