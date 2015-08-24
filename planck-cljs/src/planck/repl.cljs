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
            [tailrecursion.cljson :refer [cljson->clj]]))

(defn- println-verbose
  [& args]
  (binding [*print-fn* *print-err-fn*]
    (apply println args)))

(declare print-error)
(declare handle-error)

(defonce st (cljs/empty-state))

(defn- known-namespaces []
  (keys (:cljs.analyzer/namespaces @st)))

(defn transit-json->cljs [json]
  (let [rdr (transit/reader :json)]
    (transit/read rdr json)))

(defn cljs->transit-json [x]
  (let [wtr (transit/writer :json)]
    (transit/write wtr x)))

(defn ^:export load-core-analysis-cache []
  (cljs/load-analysis-cache! st 'cljs.core
    (transit-json->cljs (js/PLANCK_LOAD "cljs/core.cljs.cache.aot.json")))
  (cljs/load-analysis-cache! st 'cljs.core$macros
    (transit-json->cljs (js/PLANCK_LOAD "cljs/core$macros.cljc.cache.json"))))

(defonce current-ns (atom 'cljs.user))

(defonce app-env (atom nil))

(defn ^:export init-app-env [verbose]
  (reset! planck.repl/app-env {:verbose verbose}))

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

(def special-doc-map
  '{.     {:forms [(.instanceMethod instance args*)
                   (.-instanceField instance)]
           :doc   "The instance member form works for methods and fields.
  They all expand into calls to the dot operator at macroexpansion time."}
    ns    {:forms [(name docstring? attr-map? references*)]
           :doc   "You must currently use the ns form only with the following caveats

    * You must use the :only form of :use
    * :require supports :as and :refer
      - both options can be skipped
      - in this case a symbol can be used as a libspec directly
        - that is, (:require lib.foo) and (:require [lib.foo]) are both
          supported and mean the same thing
      - prefix lists are not supported
    * The only option for :refer-clojure is :exclude
    * :import is available for importing Google Closure classes
      - ClojureScript types and records should be brought in with :use
        or :require :refer, not :import ed
    * Macros are written in Clojure, and are referenced via the new
      :require-macros / :use-macros options to ns
      - :require-macros and :use-macros support the same forms that
        :require and :use do

  Implicit macro loading: If a namespace is required or used, and that
  namespace itself requires or uses macros from its own namespace, then
  the macros will be implicitly required or used using the same
  specifications. This oftentimes leads to simplified library usage,
  such that the consuming namespace need not be concerned about
  explicitly distinguishing between whether certain vars are functions
  or macros.

  Inline macro specification: As a convenience, :require can be given
  either :include-macros true or :refer-macros [syms...]. Both desugar
  into forms which explicitly load the matching Clojure file containing
  macros. (This works independently of whether the namespace being
  required internally requires or uses its own macros.) For example:

  (ns testme.core
  (:require [foo.core :as foo :refer [foo-fn] :include-macros true]
            [woz.core :as woz :refer [woz-fn] :refer-macros [app jx]]))

  is sugar for

  (ns testme.core
  (:require [foo.core :as foo :refer [foo-fn]]
            [woz.core :as woz :refer [woz-fn]])
  (:require-macros [foo.core :as foo]
                   [woz.core :as woz :refer [app jx]]))"}
    def   {:forms [(def symbol doc-string? init?)]
           :doc   "Creates and interns a global var with the name
  of symbol in the current namespace (*ns*) or locates such a var if
  it already exists.  If init is supplied, it is evaluated, and the
  root binding of the var is set to the resulting value.  If init is
  not supplied, the root binding of the var is unaffected."}
    do    {:forms [(do exprs*)]
           :doc   "Evaluates the expressions in order and returns the value of
  the last. If no expressions are supplied, returns nil."}
    if    {:forms [(if test then else?)]
           :doc   "Evaluates test. If not the singular values nil or false,
  evaluates and yields then, otherwise, evaluates and yields else. If
  else is not supplied it defaults to nil."}
    new   {:forms [(Constructor. args*) (new Constructor args*)]
           :url   "java_interop#new"
           :doc   "The args, if any, are evaluated from left to right, and
  passed to the JavaScript constructor. The constructed object is
  returned."}
    quote {:forms [(quote form)]
           :doc   "Yields the unevaluated form."}
    recur {:forms [(recur exprs*)]
           :doc   "Evaluates the exprs in order, then, in parallel, rebinds
  the bindings of the recursion point to the values of the exprs.
  Execution then jumps back to the recursion point, a loop or fn method."}
    set!  {:forms [(set! var-symbol expr)
                   (set! (.- instance-expr instanceFieldName-symbol) expr)]
           :url   "vars#set"
           :doc   "Used to set vars and JavaScript object fields"}
    throw {:forms [(throw expr)]
           :doc   "The expr is evaluated and thrown."}
    try   {:forms [(try expr* catch-clause* finally-clause?)]
           :doc   "catch-clause => (catch classname name expr*)
  finally-clause => (finally expr*)
  Catches and handles JavaScript exceptions."}
    var   {:forms [(var symbol)]
           :doc   "The symbol must resolve to a var, and the Var object
itself (not its value) is returned. The reader macro #'x expands to (var x)."}})

(def repl-special-doc-map
  '{in-ns          {:arglists ([name])
                    :doc      "Sets *cljs-ns* to the namespace named by the symbol, creating it if needed."}
    require        {:arglists ([& args])
                    :doc      "  Loads libs, skipping any that are already loaded. Each argument is
  either a libspec that identifies a lib or a flag that modifies how all the identified
  libs are loaded. Use :require in the ns macro in preference to calling this
  directly.

  Libs

  A 'lib' is a named set of resources in classpath whose contents define a
  library of ClojureScript code. Lib names are symbols and each lib is associated
  with a ClojureScript namespace. A lib's name also locates its root directory
  within classpath using Java's package name to classpath-relative path mapping.
  All resources in a lib should be contained in the directory structure under its
  root directory. All definitions a lib makes should be in its associated namespace.

  'require loads a lib by loading its root resource. The root resource path
  is derived from the lib name in the following manner:
  Consider a lib named by the symbol 'x.y.z; it has the root directory
  <classpath>/x/y/, and its root resource is <classpath>/x/y/z.clj. The root
  resource should contain code to create the lib's namespace (usually by using
  the ns macro) and load any additional lib resources.

  Libspecs

  A libspec is a lib name or a vector containing a lib name followed by
  options expressed as sequential keywords and arguments.

  Recognized options:
  :as takes a symbol as its argument and makes that symbol an alias to the
    lib's namespace in the current namespace.
  :refer takes a list of symbols to refer from the namespace..
  :refer-macros takes a list of macro symbols to refer from the namespace.
  :include-macros true causes macros from the namespace to be required.

  Flags

  A flag is a keyword.
  Recognized flags: :reload, :reload-all, :verbose
  :reload forces loading of all the identified libs even if they are
    already loaded
  :reload-all implies :reload and also forces loading of all libs that the
    identified libs directly or indirectly load via require or use
  :verbose triggers printing information about each load, alias, and refer

  Example:

  The following would load the library clojure.string :as string.

  (require '[clojure/string :as string])"}
    require-macros {:arglists ([& args])
                    :doc      "Similar to the require REPL special function but
    only for macros."}
    import         {:arglists ([& import-symbols-or-lists])
                    :doc      "import-list => (closure-namespace constructor-name-symbols*)

  For each name in constructor-name-symbols, adds a mapping from name to the
  constructor named by closure-namespace to the current namespace. Use :import in the ns
  macro in preference to calling this directly."}
    load-file      {:arglists ([name])
                    :doc     "Sequentially read and evaluate the set of forms contained in the file."}
    doc            {:arglists ([name])
                    :doc      "Prints documentation for a var or special form given its name"}
    source         {:arglists ([name])
                    :doc      "Prints the source code for the given symbol, if it can find it.\n  This requires that the symbol resolve to a Var defined in a\n  namespace for which the source is available.\n\n  Example: (source filter)"}
    pst            {:arglists ([] [e])
                    :doc      "Prints a stack trace of the exception.\n  If none supplied, uses the root cause of the most recent repl exception (*e)"}})

(defn repl-special? [form]
  (and (seq? form) (repl-special-doc-map (first form))))

(defn- special-doc [name-symbol]
  (assoc (special-doc-map name-symbol)
    :name name-symbol
    :special-form true))

(defn- repl-special-doc [name-symbol]
  (assoc (repl-special-doc-map name-symbol)
    :name name-symbol
    :repl-special-function true))

(defn- process-in-ns
  [argument]
  (cljs/eval
    st
    argument
    {:ns      @current-ns
     :context :expr
     :verbose (:verbose @app-env)}
    (fn [result]
      (if (and (map? result) (:error result))
        (print-error (:error result) false)
        (let [ns-name result]
          (if-not (symbol? ns-name)
            (println "Argument to in-ns must be a symbol.")
            (if (some (partial = ns-name) (known-namespaces))
              (reset! current-ns ns-name)
              (let [ns-form `(~'ns ~ns-name)]
                (cljs/eval
                  st
                  ns-form
                  {:ns      @current-ns
                   :context :expr
                   :verbose (:verbose @app-env)}
                  (fn [{e :error}]
                    (if e
                      (print-error e false)
                      (reset! current-ns ns-name))))))))))))

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
  [kind cb specs]
  (try
    (let [is-self-require? (and (= :kind :require) (self-require? specs))
          [target-ns restore-ns]
          (if-not is-self-require?
            [@current-ns nil]
            ['cljs.user @current-ns])]
      (cljs/eval
        st
        (let [ns-form (if (= kind :import)
                        `(~'ns ~target-ns
                           (~kind
                             ~@(map (fn [quoted-spec-or-kw]
                                      (if (keyword? quoted-spec-or-kw)
                                        quoted-spec-or-kw
                                        (second quoted-spec-or-kw)))
                                 specs)))
                        `(~'ns ~target-ns
                           (~kind
                             ~@(-> specs canonicalize-specs process-reloads!))))]
          (when (:verbose @app-env)
            (println-verbose "Implementing"
              (name kind)
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
            (handle-error e false false))
          (cb))))
    (catch :default e
      (handle-error e true false))))

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
            (handle-error e false false)))))
    (catch js/Error e
      (handle-error e false false))
    (catch :default e
      (handle-error e true false))))

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
  (let [namespace-candidates (map str (known-namespaces))
        top-form? (re-find #"^\s*\(\s*[^()\s]*$" buffer)
        typed-ns (second (re-find #"(\b[a-zA-Z-.]+)/[a-zA-Z-]+$" buffer))
        all-candidates (set (if typed-ns
                              (completion-candidates-for-ns (symbol typed-ns) false)
                              (concat namespace-candidates
                                (completion-candidates-for-ns 'cljs.core false)
                                (completion-candidates-for-ns @current-ns true)
                                (when top-form?
                                  (concat
                                    (map str (keys special-doc-map))
                                    (map str (keys repl-special-doc-map)))))))]
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

(defn caching-js-eval
  [{:keys [path name source cache]}]
  (let [cache (or cache (get-in @st [::ana/namespaces name]))] ;; Workaround CLJS-1433
    (when (and path source cache)
      (js/PLANCK_CACHE path source (cljs->transit-json cache)))
    (js/eval source)))

(defn extension->lang [extension]
  (if (= ".js" extension)
    :js
    :clj))

(defn load-and-callback! [path extension cb]
  (let [full-path (str path extension)]
    (when-let [source (js/PLANCK_LOAD full-path)]
      (cb (merge
            {:lang   (extension->lang extension)
             :source source}
            (when-not (= ".js" extension)
              (let [precompiled-js (js/PLANCK_LOAD (str path ".js"))
                    cache-json (js/PLANCK_LOAD (str full-path ".cache.json"))]
                (when (and precompiled-js cache-json)
                  (when (:verbose @app-env)
                    (println-verbose "Loading precompiled JS and analysis cache for" full-path))
                  {:lang   :js
                   :source precompiled-js
                   :cache  (transit-json->cljs cache-json)})))))
      :loaded)))

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

(defn- handle-error [e include-stacktrace? in-exit-context?]
  (let [cause (or (.-cause e) e)
        is-planck-exit-exception? (= "PLANCK_EXIT" (.-message cause))]
    (when-not is-planck-exit-exception?
      (print-error e include-stacktrace?))
    (if (and in-exit-context? (not is-planck-exit-exception?))
      (js/PLANCK_SET_EXIT_VALUE 1)
      (set! *e e))))

(defn ^:export run-main [main-ns & args]
  (let [main-args (js->clj args)]
    (binding [cljs/*load-fn* load
              cljs/*eval-fn* caching-js-eval]
      (process-require
        :require
        (fn [_]
          (cljs/eval-str st
            (str "(var -main)")
            nil
            {:ns         (symbol main-ns)
             :source-map true
             :context    :expr}
            (fn [{:keys [ns value error] :as ret}]
              (try
                (apply value args)
                (catch :default e
                  (handle-error e true true))))))
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

(defn get-var [env sym]
  (let [var (with-compiler-env st (resolve env sym))
        var (or var
              (if-let [macro-var (with-compiler-env st
                                   (resolve env (symbol "cljs.core$macros" (name sym))))]
                (update (assoc macro-var :ns 'cljs.core)
                  :name #(symbol "cljs.core" (name %)))))]
    (if (= (namespace (:name var)) (str (:ns var)))
      (update var :name #(symbol (name %)))
      var)))

(defn- get-file-source [filepath]
  (if (symbol? filepath)
    (let [without-extension (s/replace
                              (s/replace (name filepath) #"\." "/")
                              #"-" "_")]
      (or
        (js/PLANCK_LOAD (str without-extension ".clj"))
        (js/PLANCK_LOAD (str without-extension ".cljc"))
        (js/PLANCK_LOAD (str without-extension ".cljs"))))
    (let [file-source (js/PLANCK_LOAD filepath)]
      (or file-source
        (js/PLANCK_LOAD (s/replace filepath #"^out/" ""))))))

(defn- fetch-source [var]
  (when-let [filepath (or (:file var) (:file (:meta var)))]
    (when-let [file-source (get-file-source filepath)]
      (let [rdr (rt/source-logging-push-back-reader file-source)]
        (dotimes [_ (dec (:line var))] (rt/read-line rdr))
        (-> (r/read {:read-cond :allow :features #{:cljs}} rdr)
          meta :source)))))

(defn ^:export execute
  "Execute source

  set in-exit-context? to true if exit handling should be performed"
  [source lang path expression? print-nil-expression? in-exit-context?]
  (binding [ana/*cljs-ns* @current-ns
            *ns* (create-ns @current-ns)
            cljs/*load-fn* load
            cljs/*eval-fn* caching-js-eval]
    (try
      (if (= "js" lang)
        (js/eval source)
        (let [expression-form (and expression? (repl-read-string source))]
          (if (repl-special? expression-form)
            (let [env (assoc (ana/empty-env) :context :expr
                                             :ns {:name @current-ns})
                  argument (second expression-form)]
              (case (first expression-form)
                in-ns (process-in-ns argument)
                require (process-require :require identity (rest expression-form))
                require-macros (process-require :require-macros identity (rest expression-form))
                import (process-require :import identity (rest expression-form))
                doc (cond
                      (special-doc-map argument) (repl/print-doc (special-doc argument))
                      (repl-special-doc-map argument) (repl/print-doc (repl-special-doc argument))
                      :else (repl/print-doc (get-var env argument)))
                source (println (fetch-source (get-var env argument)))
                pst (let [expr (or argument '*e)]
                      (try (cljs/eval st
                             expr
                             {:ns      @current-ns
                              :context :expr}
                             print-error)
                           (catch js/Error e (prn :caught e))))
                load-file (let [filename argument]
                            (process-load-file filename)))
              (when print-nil-expression?
                (prn nil)))

            (cljs/eval-str
              st
              source
              (if expression? "Expression" "File")
              (merge
                {:ns           @current-ns
                 :source-map   false
                 :verbose      (:verbose @app-env)
                 :cache-source (fn [x cb]
                                 (when (and path (:source x))
                                   (js/PLANCK_CACHE path (:source x) nil))
                                 (cb {:value nil}))}
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
                  (handle-error error true in-exit-context?))))))))
    (catch :default e
      (handle-error e true in-exit-context?))))
