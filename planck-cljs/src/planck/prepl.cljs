(ns planck.prepl
  "Planck PREPL implementation."
  (:refer-clojure)
  (:require
    [cljs.repl]
    [cljs.spec.alpha :as s]
    [planck.repl :as repl]))

(defn- out-fn
  [input & etc]
  (binding [*print-fn* js/PLANCK_PRINT_FN]
    (let [v (:val input)]
      (prn (as-> input m
             (if (#{:ret :tag} (:tag m))
               (assoc m :val (if (string? v) (identity v) (pr-str v)))
               (identity m))
             (into {} (filter (comp some? val) m)))))))

(defn ^:export execute
  [source-text set-ns session-id]
  (binding [*print-fn* #(out-fn {:tag :out :val %1})
            *print-err-fn* #(out-fn {:tag :err :val %1})]
    (try
      (let [print-value-fn  #(out-fn {:tag  :ret
                                      :val  %1
                                      :ns   (:ns %2)
                                      :ms   (:ms %2)
                                      :form (:form %2)})
            handle-error-fn #(out-fn {:tag       :ret
                                      :val       (cljs.repl/Error->map %1)
                                      :ns        (:ns %2)
                                      :form      (:form %2)
                                      :exception true})
            opts            {:expression?           true
                             :handle-error-fn       handle-error-fn
                             :include-extra-opts?   true
                             :include-stacktrace?   true
                             :print-nil-expression? true
                             :print-value-fn        print-value-fn
                             :session-id            session-id
                             :set-ns                set-ns
                             :show-indicator?       false
                             :theme-id              "dumb"
                             :timer?                true}]
        (repl/execute ["text" source-text] opts))
      (catch :default e
        (println e)))))
