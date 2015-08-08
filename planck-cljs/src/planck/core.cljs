(ns planck.core
  (:require-macros [cljs.env.macros :refer [with-compiler-env]])
  (:require
    [cljs.analyzer :as ana]
    [cljs.tools.reader :as r]
    [cljs.tagged-literals :as tags]
    [cljs.source-map :as sm]

            [cljs.js :as cljs]
            [cljs.repl :as repl]
            [cljs.stacktrace :as st]
            [cognitect.transit :as transit]
            [tailrecursion.cljson :refer [cljson->clj]]
            [planck.io]))

(defonce st (cljs/empty-state))

(defn ^:export load-core-analysis-cache [json]
  ;(println "Loading analysis cache")
  (let [rdr (transit/reader :json)
        cache (transit/read rdr json)]
    (cljs.js/load-analysis-cache! st 'cljs.core cache)))

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

(def repl-specials '#{in-ns require require-macros doc pst})

(defn repl-special? [form]
  (and (seq? form) (repl-specials (first form))))

(def repl-special-doc-map
  '{in-ns          {:arglists ([name])
                    :doc      "Sets *cljs-ns* to the namespace named by the symbol, creating it if needed."}
    require        {:arglists ([& args])
                    :doc      "Loads libs, skipping any that are already loaded."}
    require-macros {:arglists ([& args])
                    :doc      "Similar to the require REPL special function but\n  only for macros."}
    doc            {:arglists ([name])
                    :doc      "Prints documentation for a var or special form given its name"}
    pst            {:arglists ([] [e])
                    :doc      "Prints a stack trace of the exception.\n  If none supplied, uses the root cause of the most recent repl exception (*e)"}})

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

(defn ^:export get-current-ns []
  (str @current-ns))

(defn completion-candidates-for-ns [ns-sym allow-private?]
  (map (comp str key)
    (filter (if allow-private?
              identity
              #(not (:private (:meta (val %)))))
      (apply merge
        ((juxt :defs :macros)
          (get (:cljs.analyzer/namespaces @planck.core/st) ns-sym))))))

(defn is-completion? [buffer-match-suffix candidate]
  (re-find (js/RegExp. (str "^" buffer-match-suffix)) candidate))

(defn ^:export get-completions [buffer]
  (let [namespace-candidates (map str
                               (keys (:cljs.analyzer/namespaces @planck.core/st)))
        top-form? (re-find #"^\s*\(\s*[^()\s]*$" buffer)
        typed-ns (second (re-find #"(\b[a-zA-Z-.]+)/[a-zA-Z-]+$" buffer))
        all-candidates (set (if typed-ns
                              (completion-candidates-for-ns (symbol typed-ns) false)
                              (concat namespace-candidates
                                      (completion-candidates-for-ns 'cljs.core false)
                                      (completion-candidates-for-ns @current-ns true)
                                      (when top-form? (map str repl-specials)))))]
    (let [buffer-match-suffix (re-find #"[a-zA-Z-]*$" buffer)
          buffer-prefix (subs buffer 0 (- (count buffer) (count buffer-match-suffix)))]
      (clj->js (if (= "" buffer-match-suffix)
                 []
                 (map #(str buffer-prefix %)
                   (sort
                     (filter (partial is-completion? buffer-match-suffix)
                       all-candidates))))))))

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

(defn require [macros-ns? sym reload]
  (cljs.js/require
    {:*compiler*     st
     :*load-fn*      load
     :*eval-fn*      cljs/js-eval}
    sym
    reload
    {:macros-ns  macros-ns?
     :verbose    (:verbose @app-env)
     :source-map true}
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
       :eval       cljs/js-eval
       :source-map true
       :context    :expr}
      (fn [{:keys [ns value error] :as ret}]
        (apply value args)))
    nil))

(defn load-core-source-maps! []
  (when-not (get (:source-maps @planck.core/st) 'planck.core)
    (swap! st update-in [:source-maps] merge {'planck.core
                                              (sm/decode
                                                (cljson->clj
                                                  (js/PLANCK_LOAD "planck/core.js.map")))
                                              'cljs.core
                                              (sm/decode
                                                (cljson->clj
                                                  (js/PLANCK_LOAD "cljs/core.js.map")))})))

(defn print-error [error]
  (let [cause (or (.-cause error) error)]
    (println (.-message cause))
    (load-core-source-maps!)
    (let [canonical-stacktrace (st/parse-stacktrace
                                 {}
                                 (.-stack cause)
                                 {:ua-product :safari}
                                 {:output-dir "file://(/goog/..)?"})]
      (println
        (st/mapped-stacktrace-str
          canonical-stacktrace
          (or (:source-maps @planck.core/st) {})
          nil)))))

(defn ^:export read-eval-print
  [source expression? print-nil-expression?]
  (binding [ana/*cljs-ns* @current-ns
            *ns* (create-ns @current-ns)
            #_ #_ r/*data-readers* tags/*cljs-data-readers*
            cljs.js/*eval-fn* cljs/js-eval]
    (let [expression-form (and expression? (repl-read-string source))]
      (if (repl-special? expression-form)
        (let [env (assoc (ana/empty-env) :context :expr
                                         :ns {:name @current-ns})]
          (case (first expression-form)
            in-ns (reset! current-ns (second (second expression-form)))
            require (require-destructure false (rest expression-form))
            require-macros (require-destructure true (rest expression-form))
            doc (if (repl-specials (second expression-form))
                  (repl/print-doc (repl-special-doc (second expression-form)))
                  (repl/print-doc
                    (let [sym (second expression-form)]
                          (with-compiler-env st (resolve env sym)))))
            pst (let [expr (or (second expression-form) '*e)]
                  (try (cljs/eval st
                                  expr
                                  {:ns   @current-ns
                                   :context :expr}
                                  print-error)
                       (catch js/Error e (prn :caught e)))))
          (prn nil))
        (try
          (cljs/eval-str
            st
            source
            (if expression? source "File")
            (merge
              {:ns         @current-ns
               :load       load
               :source-map false
               :verbose    (:verbose @app-env)}
              (when expression?
                {:context       :expr
                 :def-emits-var true}))
            (fn [{:keys [ns value error] :as ret}]
              (if expression?
                (when-not error
                  (when (or print-nil-expression?
                            (not (nil? value)))
                    (prn value))
                  (when-not
                      (or ('#{*1 *2 *3 *e} expression-form)
                          (ns-form? expression-form))
                    (set! *3 *2)
                    (set! *2 *1)
                    (set! *1 value))
                  (reset! current-ns ns)
                  nil))
              (when error
                (set! *e error)
                (print-error error))))
          (catch :default e
            (set! *e e)
            (print-error e)))))))
