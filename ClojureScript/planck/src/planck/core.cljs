(ns planck.core
  (:require-macros [cljs.env.macros :refer [with-compiler-env]])
  (:require [cljs.js :as cljs]
            [cljs.tagged-literals :as tags]
            [cljs.tools.reader :as r]
            [cljs.tools.reader.reader-types :refer [string-push-back-reader]]
            [cljs.analyzer :as ana]
            [cljs.repl :as repl]
            [planck.io]))

(defonce st (cljs/empty-state))

(defonce current-ns (atom 'cljs.user))

(defonce app-env (atom nil))

(defn map-keys [f m]
  (reduce-kv (fn [r k v] (assoc r (f k) v)) {} m))

(defn ^:export init-app-env [app-env]
  (reset! planck.core/app-env (map-keys keyword (cljs.core/js->clj app-env))))

(defn repl-read-string [line]
  (r/read-string {:read-cond :allow :features #{:cljs}} line))

(defn ^:export is-readable? [line]
  (binding [r/*data-readers* tags/*cljs-data-readers*]
    (try
      (repl-read-string line)
      true
      (catch :default _
        false))))

(defn ns-form? [form]
  (and (seq? form) (= 'ns (first form))))

(def repl-specials '#{in-ns require require-macros doc})

(defn repl-special? [form]
  (and (seq? form) (repl-specials (first form))))

(def repl-special-doc-map
  '{in-ns          {:arglists ([name])
                    :doc      "Sets *cljs-ns* to the namespace named by the symbol, creating it if needed."}
    require        {:arglists ([& args])
                    :doc      "Loads libs, skipping any that are already loaded."}
    require-macros {:arglists ([& args])
                    :doc      "Similar to the require REPL special function but
                    only for macros."}
    doc            {:arglists ([name])
                    :doc      "Prints documentation for a var or special form given its name"}})

(defn- repl-special-doc [name-symbol]
  (assoc (repl-special-doc-map name-symbol)
    :name name-symbol
    :repl-special-function true))


(defn resolve
  "Given an analysis environment resolve a var. Analogous to
   clojure.core/resolve"
  [env sym]
  {:pre [(map? env) (symbol? sym)]}
  (try
    (ana/resolve-var env sym
      (ana/confirm-var-exists-throw))
    (catch :default _
      (ana/resolve-macro-var env sym))))

(defn ^:export print-prompt []
  (print (str @current-ns "=> ")))

(defn extension->lang [extension]
  (if (= ".js" extension)
    :js
    :clj))

(defn load-and-callback! [path extension cb]
  (when-let [source (js/PLANCK_LOAD (str path extension))]
    (cb {:lang   (extension->lang extension)
         :source source})
    :loaded))

(defn load [{:keys [name macros path] :as full} cb]
  #_(prn full)
  (loop [extensions (if macros
                      [".clj" ".cljc"]
                      [".cljs" ".cljc" ".js"])]
    (if extensions
      (when-not (load-and-callback! path (first extensions) cb)
        (recur (next extensions)))
      (cb nil))))

(defn eval [{:keys [source]}]
  (js/eval source))

(defn require [macros-ns? sym reload]
  (cljs.js/require
    {:*compiler*     st
     :*data-readers* tags/*cljs-data-readers*
     :*load-fn*      load
     :*eval-fn*      eval}
    sym
    reload
    {:macros-ns macros-ns?
     :verbose   true}
    (fn [res]
      #_(println "require result:" res))))

(defn require-destructure [macros-ns? args]
  (let [[[_ sym] reload] args]
    (require macros-ns? sym reload)))

(defn ^:export run-main [main-ns args]
  (let [main-args (js->clj args)]
    (require false (symbol main-ns) nil)
    (cljs/eval-str st
      (str "(var -main)")
      nil
      {:ns         (symbol main-ns)
       :load       load
       :eval       eval
       :source-map false
       :context    :expr}
      (fn [{:keys [ns value error] :as ret}]
        (apply value args)))
    nil))

(defn ^:export read-eval-print [line]
  (binding [ana/*cljs-ns* @current-ns
            *ns* (create-ns @current-ns)
            r/*data-readers* tags/*cljs-data-readers*]
    (let [env (assoc (ana/empty-env) :context :expr
                                     :ns {:name @current-ns})
          form (repl-read-string line)]
      (if (repl-special? form)
        (do
          (case (first form)
            in-ns (reset! current-ns (second (second form)))
            require (require-destructure false (rest form))
            require-macros (require-destructure true (rest form))
            doc (if (repl-specials (second form))
                  (repl/print-doc (repl-special-doc (second form)))
                  (repl/print-doc
                    (let [sym (second form)
                          var (with-compiler-env st
                                (resolve env sym))]
                      (:meta var)))))
          (prn nil))
        (cljs/eval-str
          st
          line
          nil
          {:ns            @current-ns
           :load          load
           :eval          eval
           :source-map    false
           :context       :expr
           :def-emits-var true}
          (fn [{:keys [ns value error] :as ret}]
            #_(prn ret)
            (if-not error
              (do
                (prn value)
                (when-not
                  (or ('#{*1 *2 *3 *e} form)
                    (ns-form? form))
                  (set! *3 *2)
                  (set! *2 *1)
                  (set! *1 value))
                (reset! current-ns ns)
                nil)
              (do
                (set! *e error)
                (println "Error occurred")
                (println (.-stack (.-cause error)))
                #_(prn (planck.stacktrace/raw-stacktrace->canonical-stacktrace
                       (.-stack (.-cause error)) {}))
                #_(prn (cljs.stacktrace/parse-stacktrace {}
                         (.-stack (.-cause error))
                         {:ua-product :safari}))))))))))