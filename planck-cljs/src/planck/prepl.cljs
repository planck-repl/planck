(ns planck.prepl
  "Planck PREPL implementation."
  (:refer-clojure :exclude [add-tap remove-tap])
  (:require
    [cljs.repl]
    [cljs.spec.alpha :as s]
    [planck.repl :as repl]
    [planck.socket.alpha :as sock]))

(defn- ^:export add-tap
  [tap]
  (clojure.core/add-tap tap))

(defn- ^:export remove-tap
  [tap]
  (clojure.core/remove-tap tap))

(defn ^:export execute
  [source-text set-ns session-id in-fn out-fn]
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

(defn- ^:export channels
  [socket]
  (let [out-fn #(binding [*print-fn* (partial sock/write socket)]
                  (let [v (:val %1)]
                    (prn (as-> %1 m
                           (if (#{:ret :tap} (:tag m))
                             (assoc m :val (if (string? v) (identity v) (pr-str v)))
                             (identity m))
                           (into {} (filter (comp some? val) m))))))]
    #js {:in  :stdin
         :out out-fn
         :tap #(out-fn {:tag :tap :val %})}))
