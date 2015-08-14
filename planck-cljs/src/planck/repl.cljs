(ns planck.repl
  (:require-macros [cljs.env.macros :refer [with-compiler-env]])
  (:require [clojure.string :as s]
            [cljs.analyzer :as ana]
            [cljs.tools.reader :as r]
            [cljs.tools.reader.reader-types :as rt]
            [cljs.tagged-literals :as tags]
            [cljs.source-map :as sm]
            [cljs.js :as cljs]
            [cljs.repl :as repl]
            [cljs.stacktrace :as st]
            [cognitect.transit :as transit]
            [tailrecursion.cljson :refer [cljson->clj]]
            [planck.io]))

(declare print-error)

(defonce st (cljs/empty-state))

(defn ^:export load-core-analysis-cache [json]
  ;(println "Loading analysis cache")
  (let [rdr (transit/reader :json)
        cache (transit/read rdr json)]
    (cljs/load-analysis-cache! st 'cljs.core cache)))

(defonce current-ns (atom 'cljs.user))

(defonce app-env (atom nil))

(defn map-keys [f m]
  (reduce-kv (fn [r k v] (assoc r (f k) v)) {} m))

(defn ^:export init-app-env [app-env]
  (reset! planck.repl/app-env (map-keys keyword (cljs.core/js->clj app-env))))

(defn repl-read-string [line]
  (r/read-string {:read-cond :allow :features #{:cljs}} line))

(defn ^:export is-readable? [line]
  (binding [r/*data-readers* tags/*cljs-data-readers*]
    (try
      (repl-read-string line)
      true
      (catch :default e
        (let [message (.-message e)]
          (if (or
                (= "EOF while reading" message)
                (= "EOF while reading string" message))
            false
            (if (= "EOF" message)
              true
              (do
                (print-error e false)
                (println)
                true))))))))

(defn ns-form? [form]
  (and (seq? form) (= 'ns (first form))))

(def repl-specials '#{in-ns require require-macros doc pst load-file})

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
                    :doc      "Prints a stack trace of the exception.\n  If none supplied, uses the root cause of the most recent repl exception (*e)"}
    load-file      {:arglists  ([filename])
                    :doc      "Loads a file"}})

(defn- repl-special-doc [name-symbol]
  (assoc (repl-special-doc-map name-symbol)
    :name name-symbol
    :repl-special-function true))

(defn- canonicalize-specs [specs]
  (letfn [(canonicalize [quoted-spec-or-kw]
            (if (keyword? quoted-spec-or-kw)
              quoted-spec-or-kw
              (as-> (second quoted-spec-or-kw) spec
                (if (vector? spec) spec [spec]))))]
    (map canonicalize specs)))

(defn- process-reloads! [specs]
  (if-let [k (some #{:reload :reload-all} specs)]
    (let [specs (->> specs (remove #{k}))]
      (if (= k :reload-all)
        (reset! cljs.js/*loaded* #{})
        (apply swap! cljs.js/*loaded* disj (map first specs)))
      specs)
    specs))

(defn- self-require? [specs]
  (some
    (fn [quoted-spec-or-kw]
      (and (not (keyword? quoted-spec-or-kw))
        (let [spec (second quoted-spec-or-kw)
              ns (if (sequential? spec)
                   (first spec)
                   spec)]
          (= ns @current-ns))))
    specs))

(defn- process-require
  [macros-ns? cb specs]
  (try
    (let [is-self-require? (self-require? specs)
          [target-ns restore-ns]
          (if-not is-self-require?
            [@current-ns nil]
            ['cljs.user @current-ns])]
      (cljs/eval
        st
        (let [ns-form `(~'ns ~target-ns
                         (~(if macros-ns?
                             :require-macros :require)
                           ~@(-> specs canonicalize-specs process-reloads!)))]
          (when (:verbose @app-env)
            (println "Implementing"
              (if macros-ns?
                "require-macros"
                "require")
              "via ns:\n  "
              (pr-str ns-form)))
          ns-form)
        {:ns      @current-ns
         :context :expr
         :verbose (:verbose @app-env)}
        (fn [{e :error}]
          (when is-self-require?
            (reset! current-ns restore-ns))
          (when e
            (print-error e false))
          (cb))))
    (catch :default e
      (print-error e))))

(defn- process-load-file
  "Given a filename, sequentially read and evaluate
   the set of forms contained in the file"
  [filename]
  (try
    (let [file-contents (or (js/PLANCK_READ_FILE filename)
                            (throw (js/Error. (str "Could not load file " filename))))]
      (cljs/eval-str
        st
       file-contents
       filename
        {:ns      @current-ns
         :verbose (:verbose @app-env)}
        (fn [{e :error}]
          (when e
            (print-error e false))
          )))
    (catch js/Error e
      (print-error e false))
    (catch :default e
      (print-error e))))

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
          (get (:cljs.analyzer/namespaces @planck.repl/st) ns-sym))))))

(defn is-completion? [buffer-match-suffix candidate]
  (re-find (js/RegExp. (str "^" buffer-match-suffix)) candidate))

(defn ^:export get-completions [buffer]
  (let [namespace-candidates (map str
                               (keys (:cljs.analyzer/namespaces @planck.repl/st)))
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

(defn- is-completely-readable? [source]
  (let [rdr (rt/indexing-push-back-reader source 1 "noname")]
    (binding [r/*data-readers* tags/*cljs-data-readers*]
      (try
        (r/read {:eof (js-obj) :read-cond :allow :features #{:cljs}} rdr)
        (nil? (rt/peek-char rdr))
        (catch :default _
          false)))))

(defn ^:export get-highlight-coords
  "Gets the highlight coordinates [line pos] for the previous matching
  brace. This is done by progressivly expanding source considered
  until a readable form is encountered with a matching brace on the
  other end. The coordinate system is such that line 0 is the current
  buffer line, line 1 is the previous line, and so on, and pos is the
  position in that line."
  [pos buffer previous-lines]
  (let [previous-lines (js->clj previous-lines)
        previous-source (s/join "\n" previous-lines)
        total-source (if (empty? previous-lines)
                       buffer
                       (str previous-source "\n" buffer))
        total-pos (+ (if (empty? previous-lines)
                       0
                       (inc (count previous-source))) pos)]
    (let [form-start
          (some identity
            (for [n (range (dec total-pos) -1 -1)]
              (let [candidate-form (subs total-source n (inc total-pos))
                    first-char (subs candidate-form 0 1)]
                (if (#{"(" "[" "{" "#"} first-char)
                  (if (is-completely-readable? candidate-form)
                    (if (= "#" first-char)
                      (inc n)
                      n)
                    nil)))))]
      (let [highlight-coords
            (if form-start
              (reduce (fn [[line-ndx start-pos] line]
                        (if (< start-pos (count line))
                          (reduced [line-ndx start-pos])
                          [(dec line-ndx) (- start-pos (inc (count line)))]))
                [(count previous-lines) form-start]
                previous-lines)
              [-1 -1])]
        (clj->js highlight-coords)))))

(defn extension->lang [extension]
  (if (= ".js" extension)
    :js
    :clj))

(defn load-and-callback! [path extension cb]
  (when-let [source (js/PLANCK_LOAD (str path extension))]
    (cb {:lang   (extension->lang extension)
         :source source})
    :loaded))

(defn closure-index []
  (let [paths-to-provides
        (map (fn [[_ path provides]]
               [path (map second
                       (re-seq #"'(.*?)'" provides))])
          (re-seq #"\ngoog\.addDependency\('(.*)', \[(.*?)\].*"
            (js/PLANCK_LOAD "goog/deps.js")))]
    (into {}
      (for [[path provides] paths-to-provides
            provide provides]
        [(symbol provide) (str "goog/" (second (re-find #"(.*)\.js$" path)))]))))

(def closure-index-mem (memoize closure-index))

(defn load [{:keys [name macros path] :as full} cb]
  (if (re-matches #"^goog/.*" path)
    (if-let [goog-path (get (closure-index-mem) name)]
      (when-not (load-and-callback! goog-path ".js" cb)
        (cb nil))
      (cb nil))
    (loop [extensions (if macros
                        [".clj" ".cljc"]
                        [".cljs" ".cljc" ".js"])]
      (if extensions
        (when-not (load-and-callback! path (first extensions) cb)
          (recur (next extensions)))
        (cb nil)))))

(defn ^:export run-main [main-ns args]
  (let [main-args (js->clj args)]
    (binding [cljs/*load-fn* load
              cljs/*eval-fn* cljs/js-eval]
      (process-require
        false
        (fn [_]
          (cljs/eval-str st
            (str "(var -main)")
            nil
            {:ns         (symbol main-ns)
             :source-map true
             :context    :expr}
            (fn [{:keys [ns value error] :as ret}]
              (apply value args))))
        `[(quote ~(symbol main-ns))]))
    nil))

(defn load-core-source-maps! []
  (when-not (get (:source-maps @planck.repl/st) 'planck.repl)
    (swap! st update-in [:source-maps] merge {'planck.repl
                                              (sm/decode
                                                (cljson->clj
                                                  (js/PLANCK_LOAD "planck/repl.js.map")))
                                              'cljs.core
                                              (sm/decode
                                                (cljson->clj
                                                  (js/PLANCK_LOAD "cljs/core.js.map")))})))

(defn print-error
  ([error]
   (print-error error true))
  ([error include-stacktrace?]
   (let [cause (or (.-cause error) error)]
     (println (.-message cause))
     (when include-stacktrace?
       (load-core-source-maps!)
       (let [canonical-stacktrace (st/parse-stacktrace
                                    {}
                                    (.-stack cause)
                                    {:ua-product :safari}
                                    {:output-dir "file://(/goog/..)?"})]
         (println
           (st/mapped-stacktrace-str
             canonical-stacktrace
             (or (:source-maps @planck.repl/st) {})
             nil)))))))

(defn ^:export read-eval-print
  [source expression? print-nil-expression?]
  (binding [ana/*cljs-ns* @current-ns
            *ns* (create-ns @current-ns)
            cljs/*load-fn* load
            cljs/*eval-fn* cljs/js-eval]
    (let [expression-form (and expression? (repl-read-string source))]
      (if (repl-special? expression-form)
        (let [env (assoc (ana/empty-env) :context :expr
                                         :ns {:name @current-ns})]
          (case (first expression-form)
            in-ns (reset! current-ns (second (second expression-form)))
            require (process-require false identity (rest expression-form))
            require-macros (process-require true identity (rest expression-form))
            doc (if (repl-specials (second expression-form))
                  (repl/print-doc (repl-special-doc (second expression-form)))
                  (repl/print-doc
                    (let [sym (second expression-form)
                          var (with-compiler-env st (resolve env sym))]
                      (if (= (namespace (:name var)) (str (:ns var)))
                        (update var :name #(symbol (name %)))
                        var))))
            pst (let [expr (or (second expression-form) '*e)]
                  (try (cljs/eval st
                         expr
                         {:ns      @current-ns
                          :context :expr}
                         print-error)
                       (catch js/Error e (prn :caught e))))
            load-file (let [filename (second expression-form)]
                        (process-load-file filename))
            )
          (prn nil))
        (try
          (cljs/eval-str
            st
            source
            (if expression? source "File")
            (merge
              {:ns         @current-ns
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
