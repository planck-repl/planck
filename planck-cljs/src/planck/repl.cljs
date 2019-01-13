(ns planck.repl
  "Planck REPL implementation."
  (:refer-clojure :exclude [resolve load-file eval])
  (:require-macros
   [cljs.env.macros :refer [with-compiler-env]]
   [planck.repl :refer [with-err-str]])
  (:require
   [cljs.analyzer :as ana]
   [cljs.compiler :as comp]
   [cljs.env :as env]
   [cljs.js :as cljs]
   [cljs.source-map :as sm]
   [cljs.spec.alpha :as s]
   [cljs.stacktrace :as st]
   [cljs.tagged-literals :as tags]
   [cljs.tools.reader :as r]
   [cljs.tools.reader.reader-types :as rt]
   [cljsjs.parinfer]
   [clojure.string :as string]
   [cognitect.transit :as transit]
   [goog.string :as gstring]
   [lazy-map.core :refer-macros [lazy-map]]
   [planck.closure :as closure]
   [planck.js-deps :as deps]
   [planck.pprint.code]
   [planck.pprint.data]
   [planck.pprint.width-adjust]
   [planck.repl-resources :refer [repl-special-doc-map special-doc-map]]
   [planck.themes :refer [get-theme]]))

;; Provide a mechanism so s/explain with core predicates can identify the predicate names

(def ^:private fn-syms
  (delay
    (into {}
      (planck.repl/make-fn-syms nil? int? array? number? not some? object? string? char? any? symbol? var?
        iterable? cloneable? inst? reduced? counted? indexed? fn? empty? coll? set? associative? ifind? sequential?
        sorted? reduceable? map? record? vector? chunked-seq? false? true? boolean? undefined? seq? seqable? ifn?
        integer? int? pos-int? neg-int? nat-int? float? double? infinite? pos? zero? neg? list? reversible? keyword?
        ident? simple-ident? qualified-ident? simple-symbol? qualified-symbol? simple-keyword? qualified-keyword?
        even? odd? volatile? map-entry? regexp? delay? realized? uuid? special-symbol? tagged-literal? seq))))

(extend-protocol s/Specize
  default
  (specize*
    ([o]
     (if-let [f-n (and (fn? o)
                       (or (@fn-syms o)
                           (#'s/fn-sym (.-name o))))]
       (s/spec-impl f-n o nil nil)
       (s/spec-impl ::s/unknown o nil nil)))
    ([o form] (s/spec-impl form o nil nil))))

;; Prefer ES6 Number.isInteger
(set! integer? (or (.-isInteger js/Number) integer?))

;; Monkey patch target-specific core fns

(set! array? (fn [x] (instance? js/Array x)))

(set! find-ns-obj (fn [ns] (let [munged-ns (munge (str ns))
                                 segs (.split munged-ns ".")]
                             (find-ns-obj* goog/global segs))))

(defn- distinct-by
  "Returns a lazy sequence of the elements of coll, removing any elements that
  return duplicate values when passed to a function f."
  ([f]
   (fn [rf]
     (let [seen (volatile! #{})]
       (fn
         ([] (rf))
         ([result] (rf result))
         ([result x]
          (let [fx (f x)]
            (if (contains? @seen fx)
              result
              (do (vswap! seen conj fx)
                  (rf result x)))))))))
  ([f coll]
   (let [step (fn step [xs seen]
                (lazy-seq
                  ((fn [[x :as xs] seen]
                     (when-let [s (seq xs)]
                       (let [fx (f x)]
                         (if (contains? seen fx)
                           (recur (rest s) seen)
                           (cons x (step (rest s) (conj seen fx)))))))
                   xs seen)))]
     (step coll #{}))))

#_(s/fdef planck.repl$macros/dir
    :args (s/cat :sym symbol?))

#_(s/fdef planck.repl$macros/doc
    :args (s/cat :sym symbol?))

#_(s/fdef planck.repl$macros/find-doc
    :args (s/cat :re-string-or-pattern (s/or :string string? :regexp regexp?)))

#_(s/fdef planck.repl$macros/source
    :args (s/cat :sym symbol?))

(def ^{:dynamic true
       :doc "*pprint-results* controls whether Planck REPL results are pretty printed.
  If it is bound to logical false, results are printed in a plain fashion.
  Otherwise, results are pretty printed."}
*pprint-results* true)

(def ^:private ^:const expression-name "Expression")
(def ^:private could-not-eval-expr (str "Could not eval " expression-name))
(def ^:private ^:const js-ext ".js")

(defn- calc-x-line [text pos line]
  (let [x (string/index-of text "\n")]
    (if (or (nil? x)
            (< pos (inc x)))
      {:cursorX    pos
       :cursorLine line}
      (recur (subs text (inc x)) (- pos (inc x)) (inc line)))))

(defn- ^:export indent-space-count
  "Given text representing a partially entered form, returns the number of
  spaces to indent a newly entered line. Returns 0 if unsuccessful."
  [text]
  (let [pos      (count text)
        balanced (js->clj (js/parinfer.indentMode text
                            (clj->js (calc-x-line text pos 0)))
                   :keywordize-keys true)]
    (if (:success balanced)
      (let [new-text (str (subs (:text balanced) 0 pos) "\n" (subs (:text balanced) pos))
            indented (js->clj (js/parinfer.parenMode new-text
                                (clj->js (calc-x-line new-text (inc pos) 0)))
                       :keywordize-keys true)]
        (if (:success indented)
          (count (take-while #(= " " %) (:line (last (:changedLines indented)))))
          0))
      0)))

(defonce ^:dynamic ^:private theme (get-theme :dumb))

(defn- println-verbose
  [& args]
  (binding [*print-fn* *print-err-fn*
            theme      (assoc theme :err-font (:verbose-font theme))]
    (apply println args)))

(declare ^{:arglists '([error]
                       [error include-stacktrace?]
                       [error include-stacktrace? printed-message])} print-error)
(declare ^{:arglists '([e include-stacktrace?])} handle-error)

(defonce ^:private st (cljs/empty-state))

(defonce ^:private current-ns (atom 'cljs.user))

(defn- current-alias-map
  []
  (->> (merge (get-in @st [::ana/namespaces @current-ns :requires])
         (get-in @st [::ana/namespaces @current-ns :require-macros]))
    (remove (fn [[k v]] (= k v)))
    (into {})))

(defn- all-ns
  "Returns a sequence of all namespaces."
  []
  (keys (::ana/namespaces @st)))

(defn- drop-macros-suffix
  [ns-name]
  (if (string/ends-with? ns-name "$macros")
    (apply str (drop-last 7 ns-name))
    ns-name))

(defn- add-macros-suffix
  [sym]
  (symbol (str (name sym) "$macros")))

(defn- eliminate-macros-suffix
  [x]
  (if (symbol? x)
    (symbol (drop-macros-suffix (namespace x)) (name x))
    x))

(defn- get-namespace
  "Gets the AST for a given namespace."
  [ns]
  {:pre [(symbol? ns)]}
  (get-in @st [::ana/namespaces ns]))

(defn- ns-syms
  "Returns a sequence of the symbols in a namespace."
  ([ns]
   (ns-syms ns (constantly true)))
  ([ns pred]
   {:pre [(symbol? ns)]}
   (->> (get-namespace ns)
     :defs
     (filter pred)
     (map key))))

(defn- public-syms
  "Returns a sequence of the public symbols in a namespace."
  [ns]
  {:pre [(symbol? ns)]}
  (ns-syms ns (fn [[_ attrs]]
                (and (not (:private attrs))
                     (not (:anonymous attrs))))))

(defn- get-aenv
  []
  (assoc (ana/empty-env)
    :ns (get-namespace @current-ns)
    :context :expr))

(defn- transit-json->cljs
  [json]
  (let [rdr (transit/reader :json)]
    (transit/read rdr json)))

(defn- cljs->transit-json
  [x]
  (let [wtr (transit/writer :json)]
    (transit/write wtr x)))

(defn- read-transit
  [json-file]
  (transit-json->cljs (first (js/PLANCK_LOAD json-file))))

(defn- load-analysis-cache
  [ns-sym cache]
  (cljs/load-analysis-cache! st ns-sym cache))

(defn- read-and-load-analysis-cache
  [ns-sym cache-json-file]
  (load-analysis-cache ns-sym (read-transit cache-json-file)))

(defn- load-core-analysis-cache
  [eager ns-sym file-prefix]
  (let [keys [:rename-macros :renames :use-macros :excludes :name :imports :requires :uses :defs :require-macros :cljs.analyzer/constants :doc]
        load (fn [key]
               (read-transit (str file-prefix (munge key) ".json")))]
    (load-analysis-cache ns-sym
      (if eager
        (zipmap keys (map load keys))
        (lazy-map
          {:rename-macros           (load :rename-macros)
           :renames                 (load :renames)
           :use-macros              (load :use-macros)
           :excludes                (load :excludes)
           :name                    (load :name)
           :imports                 (load :imports)
           :requires                (load :requires)
           :uses                    (load :uses)
           :defs                    (load :defs)
           :require-macros          (load :require-macros)
           :cljs.analyzer/constants (load :cljs.analyzer/constants)
           :doc                     (load :doc)})))))

(defn- load-core-analysis-caches
  [eager]
  (load-core-analysis-cache eager 'cljs.core "cljs/core.cljs.cache.aot.")
  (load-core-analysis-cache eager 'cljs.core$macros "cljs/core$macros.cljc.cache."))

(defn- side-load-ns
  [ns-sym]
  (when (nil? (get-in @st [::ana/namespaces ns-sym]))
    (let [ns-sym-str          (name ns-sym)
          analysis-cache-file (str (string/replace ns-sym-str "." "/") ".cljs.cache.json")]
      (read-and-load-analysis-cache ns-sym analysis-cache-file)
      (case ns-sym
        planck.http (goog.require "planck.http")
        planck.io (goog.require "planck.io"))
      (swap! cljs.js/*loaded* conj ns-sym))))

(defonce ^:private app-env (atom nil))

(defn- read-opts-from-file
  [file]
  (when-let [contents (first (js/PLANCK_READ_FILE file))]
    (try
      (r/read-string contents)
      (catch :default e
        {:failed-read (.-message e)}))))

(declare default-session-state)

(defn- setup-asserts [elide-asserts]
  (set! *assert* (not elide-asserts))
  (swap! default-session-state assoc :*assert* *assert*))

(defn- setup-print-namespace-maps [print-namespace-maps]
  (set! *print-namespace-maps* print-namespace-maps)
  (swap! default-session-state assoc :*print-namespace-maps* *print-namespace-maps*))

(defn- compile?
  []
  (some? (:optimizations @app-env)))

(defn- normalize-closure-defines [defines]
  (into {}
    (map (fn [[k v]]
           [(if (symbol? k) (str (comp/munge k)) k) v])
      defines)))

(defn- init-closure-defines
  [defines]
  (when defines
    (set! (.. js/goog -global -CLOSURE_UNCOMPILED_DEFINES)
      (clj->js (normalize-closure-defines defines)))))

(defn- init-warnings
  [warnings warn-on-undeclared]
  (set! ana/*cljs-warnings* (merge
                              ana/*cljs-warnings*
                              (if (or (true? warnings)
                                      (false? warnings))
                                (zipmap (keys ana/*cljs-warnings*) (repeat warnings))
                                warnings)
                              (zipmap
                                [:unprovided :undeclared-var
                                 :undeclared-ns :undeclared-ns-form]
                                (repeat (if (false? warnings)
                                          false
                                          warn-on-undeclared)))
                              {:infer-warning false})))

(defn- compile-opts->edns [compile-opts]
  (cond
    (string/starts-with? compile-opts "{")
    [compile-opts]

    (string/includes? compile-opts ":")
    (mapcat compile-opts->edns (string/split compile-opts #":"))

    (string/starts-with? compile-opts "@")
    [(first (js/PLANCK_LOAD (subs compile-opts 1)))]

    (string/starts-with? compile-opts "@/")
    [(first (js/PLANCK_LOAD (subs compile-opts 2)))]

    :else
    [(first (js/PLANCK_READ_FILE compile-opts))]))

(defn- read-compile-opts [compile-opts]
  (map r/read-string (compile-opts->edns compile-opts)))

(defn- read-compile-optss [compile-optss]
  (apply merge (mapcat read-compile-opts compile-optss)))

(def ^:private passthrough-compiler-opts
  [:spec-skip-macros])

(defn- setup-passthrough-compiler-opts [opts]
  (swap! st update :options merge (select-keys opts passthrough-compiler-opts)))

(defn- ^:export init
  [repl verbose cache-path checked-arrays static-fns fn-invoke-direct elide-asserts optimizations compile-optss]
  (when (exists? *command-line-args*)
    (set! ^:cljs.analyzer/no-resolve *command-line-args* (-> js/PLANCK_INITIAL_COMMAND_LINE_ARGS js->clj seq)))
  (load-core-analysis-caches repl)
  (let [opts (merge {}
               (read-compile-optss compile-optss)
               (read-opts-from-file "opts.clj"))]
    (setup-passthrough-compiler-opts opts)
    (reset! planck.repl/app-env (merge {:repl          repl
                                        :verbose       verbose
                                        :cache-path    cache-path
                                        :elide-asserts elide-asserts
                                        :opts          opts}
                                  (when (contains? opts :verbose)
                                    {:verbose (:verbose opts)})
                                  (when checked-arrays
                                    {:checked-arrays (keyword checked-arrays)})
                                  (when (contains? opts :checked-arrays)
                                    {:checked-arrays (:checked-arrays opts)})
                                  (when static-fns
                                    {:static-fns true})
                                  (when (contains? opts :static-fns)
                                    {:static-fns (:static-fns opts)})
                                  (when fn-invoke-direct
                                    {:fn-invoke-direct true})
                                  (when (contains? opts :fn-invoke-direct)
                                    {:fn-invoke-direct opts})
                                  (when (contains? opts :elide-asserts)
                                    {:elide-asserts (:elide-asserts opts)})
                                  (when (not= optimizations "none")
                                    {:optimizations (keyword optimizations)})
                                  (when (contains? opts :optimizations)
                                    {:optimizations (:optimizations opts)})))
    (init-closure-defines (:closure-defines opts))
    (init-warnings (:warnings opts) (:warn-on-undeclared opts true))
    (deps/index-opts opts)
    (deps/index-js-libs)
    (let [index @deps/js-lib-index]
      (swap! st assoc :js-dependency-index (into index
                                             (map (fn [[k v]] [(str k) v]))
                                             index))))
  (setup-asserts (:elide-asserts @planck.repl/app-env))
  (setup-print-namespace-maps repl))

(defn- read-chars
  [reader]
  (lazy-seq
    (when-let [ch (rt/read-char reader)]
      (cons ch (read-chars reader)))))

;; Small hack to allow tests to be independent of tools.reader 1.0.0 vs. 1.0.2
;; Useful until https://github.com/mfikes/planck/issues/521 is resolved
(defn- ensure-period
  [e]
  (if (and (= :reader-exception (:type (ex-data e)))
           (not (string/ends-with? (ex-message e) ".")))
    (ex-info (str (ex-message e) ".") (ex-data e) e)
    e))

(declare ^{:arglists '([])} make-base-eval-opts)

(defn- ensure-ns [ns-name]
  (when (not-any? #{ns-name} (all-ns))
    (let [ns-form `(~'ns ~ns-name)]
      (cljs/eval
        st
        ns-form
        (make-base-eval-opts)
        (fn [{e :error}]
          (when e
            (print-error e false))))))
  ns-name)

(declare ^{:arglists '([ns name] [ns name val])} intern)

(defn- data-reader-var [sym]
  (intern (ensure-ns (symbol (namespace sym)))
    (symbol (name sym))))

(defn- get-data-readers
  "Returns the merged data reader mappings."
  []
  (try
    (reduce (fn [data-readers [url source]]
              (let [mappings (r/read-string source)]
                (when-not (map? mappings)
                  (throw (ex-info (str "Not a valid data-reader map")
                           {:url url})))
                (reduce-kv (fn [data-readers tag fn-sym]
                             (when-not (symbol? tag)
                               (throw (ex-info (str "Invalid form in data-reader file")
                                        {:url  url
                                         :form tag})))
                             (let [fn-var (data-reader-var fn-sym)]
                               (when (and (get data-readers tag)
                                          (not= (get mappings tag) fn-var))
                                 (throw (ex-info "Conflicting data-reader mapping"
                                          {:url      url
                                           :conflict tag
                                           :mappings data-readers})))
                               (assoc data-readers tag fn-var)))
                  data-readers
                  mappings)))
      {}
      (js/PLANCK_LOAD_DATA_READERS_FILES))
    (catch :default e
      (print-error e :pst))))

(def ^:private ^:dynamic *data-readers* nil)

(defn- data-readers []
  (merge tags/*cljs-data-readers* *data-readers*))

(defn- repl-read-string
  "Returns a vector of the first read form, and any balance text."
  [source]
  (try
    (binding [ana/*cljs-ns*    @current-ns
              *ns*             (create-ns @current-ns)
              env/*compiler*   st
              r/*data-readers* (data-readers)
              r/resolve-symbol ana/resolve-symbol
              r/*alias-map*    (current-alias-map)]
      (let [reader (rt/string-push-back-reader source)]
        [(r/read {:read-cond :allow :features #{:cljs}} reader) (apply str (read-chars reader))]))
    (catch :default e
      (throw (ensure-period e)))))

(def ^:private eof (js-obj))

(defn- eof-while-reading?
  [e]
  (string/includes? (ex-message e) "EOF"))

(defn- eof-guarded-read
  "Returns first readable form or `eof` if EOF"
  [source-text]
  (try
    (first (repl-read-string source-text))
    (catch :default e
      (if (eof-while-reading? e)
        eof
        (throw e)))))

(defn- ^:export is-readable?
  "Returns a string representing any text after the first readable form, nor
  nil if nothing readable."
  [source]
  (try
    (second (repl-read-string source))
    (catch :default e
      (cond
        (eof-while-reading? e) nil
        :else ""))))

(defn- seq-form-starting-with-sym?
  [form sym]
  (and (seq? form) (= sym (first form))))

(defn- ns-form?
  [form]
  (seq-form-starting-with-sym? form 'ns))

(defn- require-form?
  [form]
  (seq-form-starting-with-sym? form 'require))

(defn- extract-namespace
  [source]
  "Extracts the namespace for source text, scanning for the first ns or
  require form."
  (let [[first-form rest-source] (try
                                   (repl-read-string source)
                                   (catch :default _
                                     nil))]
    (cond
      (ns-form? first-form)
      (second first-form)

      (require-form? first-form)
      'cljs.user

      (string/blank? rest-source)
      'cljs.user

      :else
      (recur rest-source))))

(defn- repl-special?
  [form]
  (and (seq? form) (repl-special-doc-map (first form))))

(defn- special-doc
  [name-symbol]
  (assoc (special-doc-map name-symbol)
    :name name-symbol
    :special-form true))

(defn- repl-special-doc
  [name-symbol]
  (assoc (repl-special-doc-map name-symbol)
    :name name-symbol
    :repl-special-function true))

(defn- source-map? []
  (if-some [source-map (-> @app-env :opts :source-map)]
    (boolean source-map)
    true))

(defn- make-base-eval-opts
  []
  (merge
    {:ns         @current-ns
     :context    :expr
     :source-map (source-map?)}
    (select-keys @app-env [:verbose :checked-arrays :static-fns :fn-invoke-direct])))

(defn- process-in-ns
  [argument]
  (cljs/eval
    st
    argument
    (make-base-eval-opts)
    (fn [result]
      (if (:error result)
        (print-error (:error result) false)
        (let [ns-name (:value result)]
          (if-not (symbol? ns-name)
            (binding [*print-fn* *print-err-fn*]
              (println "Argument to in-ns must be a symbol."))
            (if (some (partial = ns-name) (all-ns))
              (reset! current-ns ns-name)
              (let [ns-form `(~'ns ~ns-name)]
                (cljs/eval
                  st
                  ns-form
                  (make-base-eval-opts)
                  (fn [{e :error}]
                    (if e
                      (print-error e false)
                      (reset! current-ns ns-name))))))))))))

(defn- resolve-var
  "Given an analysis environment resolve a var. Analogous to
   clojure.core/resolve"
  [env sym]
  {:pre [(map? env) (symbol? sym)]}
  (try
    (ana/resolve-var env sym
      (ana/confirm-var-exists-throw))
    (catch :default _
      (ana/resolve-macro-var env sym))))

(defn- ^:export get-current-ns
  []
  (str @current-ns))

(defn- completion-candidates-for-ns
  [ns-sym allow-private?]
  (if (string/starts-with? (str ns-sym) "goog")
    (if (find-ns ns-sym)
      (into [] (js-keys (.getObjectByName js/goog (str ns-sym))))
      [])
    (map (comp str key)
      (into []
        (comp
          (filter (if allow-private?
                    identity
                    #(not (:private (val %)))))
          (remove #(:anonymous (val %))))
        (apply merge
          ((juxt :defs :macros)
           (get-namespace ns-sym)))))))

(defn- completion-candidates-for-current-ns []
  (let [cur-ns @current-ns]
    (into (completion-candidates-for-ns cur-ns true)
      (comp (mapcat keys) (map str))
      ((juxt :renames :rename-macros :uses :use-macros) (get-namespace cur-ns)))))

(defn- is-completion?
  [match-suffix candidate]
  (let [escaped-suffix (string/replace match-suffix #"[-\/\\^$*+?.()|\[\]{}]" "\\$&")]
    (re-find (js/RegExp. (str "^" escaped-suffix) "i") candidate)))

(def ^:private keyword-completions
  [:require :require-macros :import
   :refer :refer-macros :include-macros
   :refer-clojure :exclude
   :keys :strs :syms
   :as :or
   :pre :post
   :let :when :while
   :clj :cljs
   :default
   :else
   :gen-class
   :keywordize-keys
   :req :req-un :opt :opt-un
   :args :ret :fn])

(def ^:private namespace-completion-exclusions
  '[planck.from.io.aviso.ansi
    planck.pprint.code
    planck.pprint.data
    planck.bundle
    planck.closure
    planck.js-deps
    planck.repl
    planck.repl-resources
    planck.themes
    clojure.core.rrb-vector
    clojure.core.rrb-vector.interop
    clojure.core.rrb-vector.nodes
    clojure.core.rrb-vector.protocols
    clojure.core.rrb-vector.rrbt
    clojure.core.rrb-vector.transients
    clojure.core.rrb-vector.trees
    cognitect.transit
    fipp.deque
    fipp.engine
    fipp.visit
    lazy-map.core
    cljs.source-map
    cljs.source-map.base64
    cljs.source-map.base64-vlq
    cljs.tools.reader.impl.commons
    cljs.tools.reader.impl.utils
    cljs.stacktrace])

(def ^:private namespace-completion-additons
  '[planck.core
    planck.io
    planck.http
    planck.shell
    planck.socket.alpha
    clojure.core
    clojure.test
    clojure.spec.alpha
    clojure.spec.test.alpha
    clojure.spec.gen.alpha
    clojure.pprint])

(defn- namespace-completions
  []
  (->> (all-ns)
    (map #(drop-macros-suffix (str %)))
    (remove (into #{} (map str namespace-completion-exclusions)))
    (concat (map str namespace-completion-additons))
    sort
    distinct))

(defn- expand-typed-ns
  "Expand the typed namespace symbol to a known namespace, consulting current
  namespace aliases if necessary."
  [alias]
  (let [alias (if (symbol-identical? alias 'clojure.core)
                'cljs.core
                alias)]
    (or (get-in st [:cljs.analyzer/namespaces alias :name])
        (alias (current-alias-map))
        alias)))

(defn- completion-candidates
  [top-form? typed-ns]
  (set (if typed-ns
         (let [expanded-ns (expand-typed-ns (symbol typed-ns))]
           (concat
             (completion-candidates-for-ns expanded-ns false)
             (completion-candidates-for-ns (add-macros-suffix expanded-ns) false)))
         (concat
           (map str keyword-completions)
           (namespace-completions)
           (map #(str % "/") (keys (current-alias-map)))
           (completion-candidates-for-ns 'cljs.core false)
           (completion-candidates-for-ns 'cljs.core$macros false)
           (completion-candidates-for-current-ns)
           (when top-form?
             (concat
               (map str (keys special-doc-map))
               (map str (keys repl-special-doc-map))))))))

(defn- spec-registered-keywords
  [ns]
  (eduction
    (filter keyword?)
    (filter #(= (str ns) (namespace %)))
    (keys (s/registry))))

(defn- local-keyword-str
  [kw]
  (str "::" (name kw)))

(defn- local-keyword
  "Returns foo for ::foo, otherwise nil"
  [buffer]
  (second (re-find #"::([a-zA-Z-]*)$" buffer)))

(defn- local-keyword-completions
  [kw-name]
  (let [kw-source (str "::" kw-name)]
    (clj->js (into [kw-source]
               (sequence
                 (comp
                   (map local-keyword-str)
                   (filter #(string/starts-with? % kw-source)))
                 (spec-registered-keywords @current-ns))))))

(defn- longest-common-prefix
  [strings]
  (let [minl (apply min (map count strings))]
    (loop [l minl]
      (if (> l 0)
        (if (every? #{(subs (first strings) 0 l)}
              (map #(subs % 0 l) (rest strings)))
          (subs (first strings) 0 l)
          (recur (dec l)))
        ""))))

(defn- ^:export get-completions
  "Returns an array of the buffer-match-suffix, along with completions for the
  entered text. If one completion is returned the line should be completed to
  match it (in which the completion may actually only be a longest prefix from
  the list of candiates), otherwise the list of completions should be
  displayed."
  [buffer]
  (if-let [kw-name (local-keyword buffer)]
    (local-keyword-completions kw-name)
    (let [top-form? (re-find #"^\s*\(\s*[^()\s]*$" buffer)
          typed-ns  (second (re-find #"\(*(\b[a-zA-Z0-9-.<>*=&?]+)/[a-zA-Z0-9-]*$" buffer))]
      (let [buffer-match-suffix (first (re-find #":?([a-zA-Z0-9-.<>*=&?]*|^\(/)$" buffer))
            completions         (sort (filter (partial is-completion? buffer-match-suffix)
                                        (completion-candidates top-form? typed-ns)))
            common-prefix (longest-common-prefix completions)]
        (if (or (empty? common-prefix)
                (= common-prefix buffer-match-suffix))
          (clj->js (into [buffer-match-suffix] completions))
          #js [buffer-match-suffix common-prefix])))))

(defn- is-completely-readable?
  [source]
  (let [rdr (rt/indexing-push-back-reader source 1 "noname")]
    (binding [ana/*cljs-ns*    @current-ns
              *ns*             (create-ns @current-ns)
              env/*compiler*   st
              r/*data-readers* (data-readers)
              r/resolve-symbol ana/resolve-symbol
              r/*alias-map*    (current-alias-map)]
      (try
        (r/read {:eof (js-obj) :read-cond :allow :features #{:cljs}} rdr)
        (nil? (rt/peek-char rdr))
        (catch :default _
          false)))))

(defn- form-start
  [total-source total-pos]
  (some identity
    (for [n (range (dec total-pos) -1 -1)]
      (let [candidate-form (subs total-source n (inc total-pos))
            first-char     (subs candidate-form 0 1)]
        (if (#{"(" "[" "{" "#"} first-char)
          (if (is-completely-readable? candidate-form)
            (if (= "#" first-char)
              (inc n)
              n)
            nil))))))

(defn- reduce-highlight-coords
  [previous-lines form-start]
  (if form-start
    (reduce (fn [[line-ndx start-pos] line]
              (if (< start-pos (count line))
                (reduced [line-ndx start-pos])
                [(dec line-ndx) (- start-pos (inc (count line)))]))
      [(count previous-lines) form-start]
      previous-lines)
    [-1 -1]))

(defn- ^:export get-highlight-coords
  "Gets the highlight coordinates [line pos] for the previous matching brace.
  This is done by progressivly expanding source considered until a readable
  form is encountered with a matching brace on the other end. The coordinate
  system is such that line 0 is the current buffer line, line 1 is the previous
  line, and so on, and pos is the position in that line."
  [pos buffer previous-lines]
  (let [previous-lines  (js->clj previous-lines)
        previous-source (string/join "\n" previous-lines)
        total-source    (if (empty? previous-lines)
                          buffer
                          (str previous-source "\n" buffer))
        total-pos       (+ (if (empty? previous-lines)
                             0
                             (inc (count previous-source))) pos)]
    (->> (form-start total-source total-pos)
      (reduce-highlight-coords previous-lines)
      clj->js)))

(defn- cache-prefix-for-path
  [path macros]
  (str (:cache-path @app-env) "/" (munge path) (when macros "$macros")))

(defn- extract-cache-metadata
  [source]
  (let [file-namespace (or (extract-namespace source)
                           'cljs.user)
        relpath        (cljs/ns->relpath file-namespace)]
    [file-namespace relpath]))

(def ^:private extract-cache-metadata-mem (memoize extract-cache-metadata))

(defn- form-compiled-by-string
  ([] (form-compiled-by-string nil))
  ([opts]
   (str "// Compiled by ClojureScript "
     *clojurescript-version*
     (when opts
       (str " " (pr-str opts))))))

(defn- read-build-affecting-options
  [source]
  (let [rdr (rt/indexing-push-back-reader source 1 "noname")]
    (binding [r/*data-readers* (data-readers)]
      (try
        (r/read {:eof (js-obj)} rdr)
        (catch :default _
          nil)))))

(defn- extract-source-build-info
  [js-source]
  (let [[cljs-ver build-affecting-options] (rest (re-find #"// Compiled by ClojureScript (\S*)(.*)?" js-source))
        build-affecting-options (when build-affecting-options
                                  (read-build-affecting-options build-affecting-options))]
    [cljs-ver build-affecting-options]))

(defn- is-macros?
  [cache]
  (string/ends-with? (str (:name cache)) "$macros"))

(defn- form-build-affecting-options
  []
  (let [m (merge
            (when-not *assert*
              {:elide-asserts true})
            (select-keys @app-env [:checked-arrays :static-fns :fn-invoke-direct :optimizations]))]
    (if (empty? m)
      nil
      m)))

;; Hack to remember which file path each namespace was loaded from
(defonce ^:private name-path (atom {}))

(declare ^{:arglists '([file suffix])} add-suffix)

(defn- js-path-for-name
  [name]
  (add-suffix (get @name-path name name) ".js"))

(defn- file-url
  "Makes a file url for use with PLANCK_EVAL"
  [path]
  (str "file:///" path))

(defn- log-cache-activity
  [read-write path cache-json sourcemap-json]
  (when (:verbose @app-env)
    (println-verbose
      (str
        (if (= :read read-write)
          "Loading"
          "Caching")
        " compiled JS "
        (when cache-json "and analysis cache ")
        (when sourcemap-json "and source map ")
        "for " path))))

(declare ^{:arglists '([sm])} strip-source-map)

(defn- write-cache
  [path name source cache]
  (when (and path source cache (:cache-path @app-env))
    (let [cache-json     (cljs->transit-json cache)
          sourcemap-json (when (source-map?)
                           (when-let [sm (get-in @planck.repl/st [:source-maps (:name cache)])]
                             (cljs->transit-json (strip-source-map sm))))]
      (log-cache-activity :write path cache-json sourcemap-json)
      (js/PLANCK_CACHE (cache-prefix-for-path path (is-macros? cache))
        (str (form-compiled-by-string (form-build-affecting-options)) "\n" source)
        cache-json
        sourcemap-json))))

(defn- js-eval
  [source source-url]
  #_(when (:verbose @app-env)
    (println-verbose (str "Evaluating JavaScript:\n" source)))
  (if source-url
    (let [exception (js/PLANCK_EVAL source source-url)]
      (when exception
        (throw exception)))
    ;; Eval in global scope
    (let [geval js/eval]
      (geval source))))

(defn- cacheable?
  [{:keys [path name source source-url cache]}]
  (and path source cache (:cache-path @app-env)))

(defn- caching-js-eval
  [{:keys [path name source source-url cache] :as all}]
  (when (cacheable? all)
    (write-cache path name source cache))
  (let [source-url (or source-url
                       (when (and (not (empty? path))
                                  (not= expression-name path))
                         (file-url (js-path-for-name name))))]
    (js-eval source source-url)))

(defn- compile
  [m]
  (closure/compile
    (merge m
      (select-keys @app-env [:optimizations :verbose]))))

(defn- compiling
  [m]
  (if (and (not (= :js (:lang m)))
           (:cache m))
    (let [{:keys [source source-map]} (compile (cond-> m
                                                 (source-map?)
                                                 (assoc :sm-data @comp/*source-map-data*)))]
      (when source-map
        (swap! st assoc-in [:source-maps (:name (:cache m))] source-map))
      (assoc m :source source))
    m))

(defn- extension->lang
  [extension]
  (if (= ".js" extension)
    :js
    :clj))

(defn- add-suffix
  [file suffix]
  (let [candidate (string/replace file #".clj[sc]?$" suffix)]
    (if (gstring/endsWith candidate suffix)
      candidate
      (str file suffix))))

(defn- bundled?
  [js-modified source-file-modified]
  (= 0 js-modified source-file-modified))                   ;; 0 means bundled

(defn- cached-js-valid?
  [js-source js-modified source-file-modified]
  (and js-source
       (or (bundled? js-modified source-file-modified)
           (and (> js-modified source-file-modified)
                (let [[cljs-ver build-affecting-options] (extract-source-build-info js-source)]
                  (and (= *clojurescript-version* cljs-ver)
                       (= build-affecting-options (form-build-affecting-options))))))))

;; Represents code for which the JS is already loaded (but for which the analysis cache may not be)
(defn- skip-load-js?
  [name]
  ('#{cljs.analyzer
      cljs.compiler
      cljs.env
      cljs.reader
      cljs.source-map
      cljs.source-map.base64
      cljs.source-map.base64-vlq
      cljs.stacktrace
      cljs.tagged-literals
      cljs.tools.reader.impl.utils
      cljs.tools.reader.reader-types
      clojure.set
      clojure.string
      cognitect.transit} name))

(defn- strip-first-line
  [source]
  (subs source (inc (string/index-of source "\n"))))

(defn- cached-callback-data
  [name path macros cache-prefix source source-modified raw-load]
  (let [path         (cond-> path
                       macros (add-suffix "$macros"))
        aname        (cond-> name
                       macros ana/macro-ns-name)
        cache-prefix (if (= :calculate-cache-prefix cache-prefix)
                       (cache-prefix-for-path (second (extract-cache-metadata-mem source)) macros)
                       cache-prefix)
        [js-source js-modified] (or (raw-load (add-suffix path ".js"))
                                    (js/PLANCK_READ_FILE (str cache-prefix ".js")))
        [cache-json _] (or (raw-load (str path ".cache.json"))
                           (js/PLANCK_READ_FILE (str cache-prefix ".cache.json")))
        [sourcemap-json _] (when (source-map?)
                             (or (raw-load (str path ".js.map.json"))
                                 (js/PLANCK_READ_FILE (str cache-prefix ".js.map.json"))))]
    (when (cached-js-valid? js-source js-modified source-modified)
      (log-cache-activity :read path cache-json sourcemap-json)
      (when (and sourcemap-json aname)
        (swap! st assoc-in [:source-maps aname] (transit-json->cljs sourcemap-json)))
      (merge {:lang   :js
              :source ""}
        (when-not (skip-load-js? name)
          {:source     (cond-> js-source (not (bundled? js-modified source-modified)) strip-first-line)
           :source-url (file-url (add-suffix path ".js"))})
        (when cache-json
          (let [cache (transit-json->cljs cache-json)]
            (cljs/load-analysis-cache! st aname cache)
            {:cache cache}))))))

(defn- load-and-callback!
  [name path load-domain macros lang cache-prefix cb]
  (let [[raw-load [source modified loaded-path]] [js/PLANCK_LOAD (when (contains? #{:classpath nil} load-domain)
                                                                   (js/PLANCK_LOAD path))]
        [raw-load [source modified loaded-path]] (if source
                                                   [raw-load [source modified loaded-path]]
                                                   [js/PLANCK_READ_FILE (when (contains? #{:filesystem nil} load-domain)
                                                                          (js/PLANCK_READ_FILE path)) path])]
    (when source
      (when name
        (swap! name-path assoc name path))
      (cb (merge
            {:lang   lang
             :source source
             :file   loaded-path}
            (when-not (= :js lang)
              (cached-callback-data name path macros cache-prefix source modified raw-load))))
      :loaded)))

(defn- closure-index* []
  (let [paths-to-deps
        (map (fn [[_ path provides requires]]
               [path
                (map second
                  (re-seq #"'(.*?)'" provides))
                (map second
                  (re-seq #"'(.*?)'" requires))])
          (re-seq #"\ngoog\.addDependency\('(.*)', \[(.*?)\], \[(.*?)\].*"
            (first (js/PLANCK_LOAD "goog/deps.js"))))]
    (into {}
      (for [[path provides requires] paths-to-deps
            provide provides]
        [(symbol provide) {:path (str "goog/" (second (re-find #"(.*)\.js$" path)))
                           :requires requires}]))))

(def ^:private closure-index (memoize closure-index*))

(defn- skip-load?
  [{:keys [name macros]}]
  (or
   (= name 'goog)
   (= name 'cljsjs.parinfer)
   (= name 'cljs.core)
   (and (= name 'clojure.core.rrb-vector.macros) macros)
   (and (= name 'cljs.js) macros)
   (and (= name 'cljs.reader) macros)
   (and (= name 'cljs.tools.reader.reader-types) macros)
   (and (= name 'tailrecursion.cljson) macros)
   (and (= name 'lazy-map.core) macros)))

(defn- load-file
  [file load-domain cb]
  (when-not (load-and-callback! nil file load-domain false :clj :calculate-cache-prefix cb)
    (cb nil)))

(declare ^{:arglists '([name])} goog-dep-source)

(defn- load-minified-libs?
  [opts]
  (= :simple (:optimizations opts)))

;; TODO: we could be smarter and only load the libs that we haven't already loaded
(defn- load-js-lib
  [name opts cb]
  (let [sources (mapcat (fn [{:keys [file file-min requires]}]
                          (let [file (or (and (load-minified-libs? opts)
                                              file-min)
                                         file)]
                            (concat (->> requires
                                      (filter #(string/starts-with? % "goog."))
                                      (map (comp goog-dep-source symbol)))
                              [(first (or (js/PLANCK_LOAD file) (js/PLANCK_READ_FILE file)))])))
                  (deps/js-libs-to-load name))]
    (cb {:lang :js
         :source (string/join "\n" sources)})
    :loaded))

(defonce ^:private goog-loaded
  (volatile! '#{goog.object
                goog.string
                goog.string.StringBuffer
                goog.array
                goog.crypt.base64
                goog.math.Long}))

(defn- goog-dep-source [name]
  (let [index (closure-index)]
    (when-let [{:keys [path]} (get index name)]
      (let [sorted-deps (remove @goog-loaded (deps/topo-sort index name))]
        (vswap! goog-loaded into sorted-deps)
        (reduce str
          (map (fn [dep-name]
                 (let [{:keys [path]} (get index dep-name)]
                   (first (js/PLANCK_LOAD (str path js-ext))))) sorted-deps))))))

(defn- load-goog
  "Loads a Google Closure implementation source file."
  [name cb]
  (if-let [source (goog-dep-source name)]
    (cb {:source source
         :lang   :js})
    (cb nil)))

(defn- load-other
  [name path macros cb]
  (loop [extensions (if macros
                      [".clj" ".cljc"]
                      [".cljs" ".cljc" ".js"])]
    (if extensions
      (when-not (load-and-callback!
                  name
                  (str path (first extensions))
                  nil
                  macros
                  (extension->lang (first extensions))
                  (cache-prefix-for-path path macros)
                  cb)
        (recur (next extensions)))
      (cb nil))))

(defn- load-cljs-nodejs
  "Loads a minimal facsimile of cljs.nodejs"
  [name path cb]
  (swap! name-path assoc name path)
  (cb {:source "(ns cljs.nodejs)\n(defn enable-util-print! [])"
       :lang :clj}))

; file here is an alternate parameter denoting a filesystem path
(defn- load
  [{:keys [name macros path file load-domain] :as full} opts cb]
  (cond
    file (load-file file load-domain cb)
    (skip-load? full) (cb {:lang   :js
                           :source ""})
    (re-matches #"^goog/.*" path) (load-goog name cb)
    (deps/js-lib? name) (load-js-lib name opts cb)
    (= name 'cljs.nodejs) (load-cljs-nodejs name path cb)
    :else (load-other name path macros cb)))

(defn- load-opts
  []
  (select-keys @app-env [:optimizations]))

(defn- load-fn
  [m cb]
  (load m (load-opts) cb))

(declare ^{:arglists '([error])} skip-cljsjs-eval-error)

(defn- handle-error
  [e include-stacktrace?]
  (do
    (print-error e include-stacktrace?)
    (if (not (:repl @app-env))
      (js/PLANCK_EXIT_WITH_VALUE 1)
      (set! *e (skip-cljsjs-eval-error e)))))

(defn- get-eval-fn []
  (cond-> caching-js-eval (compile?) (comp compiling)))

(defn- run-main-impl
  [main args]
  (try
    (apply main args)
    (catch :default e
      (handle-error e true))))

(defn- ^:export run-main
  [main-ns & args]
  (let [main-args (js->clj args)
        opts      (make-base-eval-opts)]
    (binding [cljs/*load-fn* load-fn
              cljs/*eval-fn* (get-eval-fn)]
      (cljs/eval st
        `(~'require (quote ~(symbol main-ns)))
        opts
        (fn [{:keys [ns value error] :as ret}]
          (if error
            (handle-error error true)
            (cljs/eval-str st
              (str "(var -main)")
              nil
              (merge opts {:ns (symbol main-ns)})
              (fn [{:keys [ns value error] :as ret}]
                (run-main-impl value main-args)))))))
    nil))

(defn- ^:export run-main-cli-fn
  []
  (when (fn? *main-cli-fn*)
    (run-main-impl *main-cli-fn* *command-line-args*)))

(defn- load-bundled-source-maps!
  [ns-syms]
  (when (source-map?)
    (let [source-map-path  (fn [ns-sym]
                             (str (cljs.js/ns->relpath ns-sym) ".js.map"))
          load-source-maps (fn [ns-sym]
                             (when-not (get-in @st [:source-maps ns-sym])
                               (if-let [sm-text (->> ns-sym
                                                  source-map-path
                                                  js/PLANCK_LOAD
                                                  first)]
                                 ;; Detect if we have source maps in need of decoding
                                 ;; or if they are AOT decoded.
                                 (if (or (string/starts-with? sm-text "{\"version\"")
                                         (string/starts-with? sm-text "{\n\"version\""))
                                   (cljs/load-source-map! st ns-sym sm-text)
                                   (swap! st assoc-in [:source-maps ns-sym] (transit-json->cljs sm-text)))
                                 (swap! st assoc-in [:source-maps ns-sym] {}))))]
      (run! load-source-maps ns-syms))))

(defn- load-core-macros-source-maps! []
  (load-bundled-source-maps! '[cljs.core$macros]))

(defonce ^:dynamic ^:private *planck-integration-tests* false)

(defn- skip-cljsjs-eval-error
  [error]
  (cond-> error
    (and (instance? ExceptionInfo error)
         (= :cljs/analysis-error (:tag (ex-data error)))
         (or (= "ERROR" (ex-message error))
             (= could-not-eval-expr (ex-message error)))
         (ex-cause error))
    ex-cause))

(defn- reader-error?
  [e]
  (= :reader-exception (:type (ex-data e))))

(defn- analysis-error?
  [e]
  (= :cljs/analysis-error (:tag (ex-data e))))

(defn- reader-or-analysis?
  "Indicates if an exception is a reader or analysis exception."
  [e]
  (or (reader-error? e)
      (analysis-error? e)))

(defn- form-demunge-map
  "Forms a map from munged function symbols (as they appear in stacktraces)
  to their unmunged forms."
  [ns]
  {:pre [(symbol? ns)]}
  (let [ns-str        (str ns)
        munged-ns-str (string/escape ns-str {\- \_ \. \$})]
    (into {} (for [sym (ns-syms ns)]
               [(str munged-ns-str "$" (munge sym)) (symbol ns-str (str sym))]))))

(def ^:private core-demunge-map
  (delay (form-demunge-map 'cljs.core)))

(defn- non-core-demunge-maps
  []
  (let [non-core-nss (remove #{'cljs.core 'cljs.core$macros} (all-ns))]
    (map form-demunge-map non-core-nss)))

(defn- lookup-sym
  [demunge-maps munged-sym]
  (some #(% munged-sym) demunge-maps))

(defn- demunge-local
  [demunge-maps munged-sym]
  (let [[_ fn local] (re-find #"(.*)_\$_(.*)" munged-sym)]
    (when fn
      (when-let [fn-sym (lookup-sym demunge-maps fn)]
        (str fn-sym " " (demunge local))))))

(defn- demunge-protocol-fn
  [demunge-maps munged-sym]
  (let [[_ ns prot fn] (re-find #"(.*)\$(.*)\$(.*)\$arity\$.*" munged-sym)]
    (when ns
      (when-let [prot-sym (lookup-sym demunge-maps (str ns "$" prot))]
        (when-let [fn-sym (lookup-sym demunge-maps (str ns "$" fn))]
          (str fn-sym " [" prot-sym "]"))))))

(defn- sym-name-starts-with?
  [prefix sym]
  (string/starts-with? (name sym) prefix))

(def ^:private gensym? (partial sym-name-starts-with? "G__"))

(def ^:private planck-native? (partial sym-name-starts-with? "PLANCK_"))

(defn- demunge-sym
  [munged-sym]
  (let [demunge-maps (cons @core-demunge-map (non-core-demunge-maps))]
    (str (or (lookup-sym demunge-maps munged-sym)
             (demunge-protocol-fn demunge-maps munged-sym)
             (demunge-local demunge-maps munged-sym)
             (if ((some-fn gensym? planck-native?) munged-sym)
               munged-sym
               (demunge munged-sym))))))

;; Revise mapped-frame so it doesn't automatically assume source files are .cljs
;; Hack by checking to see if .cljs file exists
(defn- mapped-frame
  [{:keys [function file line column]} sms opts]
  (let [no-source-file?      (if-not file true (string/starts-with? file "<"))
        [line' column' call] (if no-source-file?
                               [line column nil]
                               (st/mapped-line-column-call sms file line column))
        exists?              (fn [file] (some? (js/PLANCK_LOAD file)))
        file'                (when-not no-source-file?
                               (if (string/ends-with? file ".js")
                                 (let [cljs-file (str (subs file 0 (- (count file) 3)) ".cljs")]
                                   (if (exists? cljs-file)
                                     cljs-file
                                     file))
                                 file))]
    {:function function
     :call     call
     :file     (if no-source-file?
                 (str "NO_SOURCE_FILE" (when file (str " " file)))
                 file')
     :line     line'
     :column   column'}))

;; Monkey-patch mapped-frame
(set! st/mapped-frame mapped-frame)

(defn- js-file? [file]
  (string/ends-with? file ".js"))

(defn- file->ns-sym [file]
  (-> file
    st/remove-ext
    (string/replace "/" ".")
    (string/replace "_" "-")
    symbol))

(defn- qualify [name file]
  (cond->> name
    (not (or (string/includes? name "/")
             (js-file? file)))
    (str (file->ns-sym file) "/")))

(defn- mapped-stacktrace-str
  ([stacktrace sms]
   (mapped-stacktrace-str stacktrace sms nil))
  ([stacktrace sms opts]
   (apply str
     (for [{:keys [function file line column]} (st/mapped-stacktrace stacktrace sms opts)
           :let [demunged (-> (str (when function (demunge-sym function)))
                            (qualify file))]
           :when (not= demunged "cljs.core/-invoke [cljs.core/IFn]")]
       (str \tab demunged " (" file (when line (str ":" line))
         (when column (str ":" column)) ")" \newline)))))

(defn- strip-source-map
  "Strips a source map down to the minimal representation needed for mapping
  stacktraces. This means we only need the :line and :col fields, we only need
  the last element in each vector of such maps, and we can eliminate
  duplicates, taking the smallest col number for each unique value."
  [sm]
  (into {}
    (map (fn [[row cols]]
           [row (->> cols
                  (map (fn [[col frames]]
                         [col [(select-keys (peek frames) [:line :col])]]))
                  (sort-by first)
                  (distinct-by second)
                  (into {}))]))
    sm))

(defonce ^:private can-show-indicator (atom false))

(defn- reset-show-indicator!
  []
  (reset! can-show-indicator (:repl @app-env)))

(defn- disable-error-indicator!
  []
  (reset! can-show-indicator false))

(defn- show-indicator?
  []
  (let [rv @can-show-indicator]
    (reset! can-show-indicator false)
    rv))

(defn- form-indicator
  [column current-ns]
  (str (apply str (take (+ 2 (count (name current-ns)) column) (repeat " "))) "^"))

(defn- get-error-column-indicator
  [error current-ns]
  (when (and (instance? ExceptionInfo error)
             (= could-not-eval-expr (ex-message error)))
    (when-let [cause (ex-cause error)]
      (when (reader-or-analysis? cause)
        (when-let [column (:column (ex-data cause))]
          (form-indicator column current-ns))))))

(defn- print-error-column-indicator
  [error]
  (let [indicator (get-error-column-indicator error @current-ns)]
    (when (and indicator
               (show-indicator?))
      (println ((:rdr-ann-err-fn theme) indicator)))))

(declare ^{:arglists '([value opts])} print-value)

(defn- file-path
  [name]
  (or (@name-path name)
      name))

(defn- location-info
  [error]
  (let [data (ex-data error)]
    (when (and (:line data)
               (:file data))
      (str " at line " (:line data) " " (file-path (:file data))))))

(def ^:private stack-truncation-functions
  #{"PLANCK_EVAL"
    "global code"
    "planck$repl$run_main_impl"
    "planck$pprint$width_adjust$force_eval"
    "planck$repl$print_value"
    "fipp$visit$IVisitor$visit_seq$arity$2"})

(defn- explain-printer
  [ed]
  (let [pr'     #(print-value %
                   {::no-newline? true
                    ::keyword-ns @current-ns
                    ::as-code?    true})
        pr-str' #(with-out-str (pr' %))]
    (if ed
      (let [problems (->> (::s/problems ed)
                       (sort-by #(- (count (:in %))))
                       (sort-by #(- (count (:path %)))))]
        (print
          (with-out-str
            ;;(prn {:ed ed})
            (doseq [{:keys [path pred val reason via in] :as prob} problems]
              (pr' val)
              (print " - failed: ")
              (if reason (print reason) (pr' (s/abbrev pred)))
              (when-not (empty? in)
                (print (str " in: " (pr-str' in))))
              (when-not (empty? path)
                (print (str " at: " (pr-str' path))))
              (when-not (empty? via)
                (print (str " spec: " (pr-str' (last via)))))
              (doseq [[k v] prob]
                (when-not (#{:path :pred :val :reason :via :in} k)
                  (print "\n\t" (pr-str' k) " ")
                  (pr' v)))
              (newline)))))
      (println "Success!"))))

;; Make the Planck explain printer be the default one, but yet still overridable
(set! s/*explain-out* explain-printer)

(defn- print-error
  ([error]
   (print-error error true))
  ([error include-stacktrace?]
   (print-error error include-stacktrace? nil))
  ([error include-stacktrace? printed-message]
   (print-error-column-indicator error)
   (let [error               (skip-cljsjs-eval-error error)
         roa?                (reader-or-analysis? error)
         print-ex-data?      (= include-stacktrace? :pst)
         include-stacktrace? (or (= include-stacktrace? :pst)
                                 (and include-stacktrace?
                                      (not roa?)))
         include-stacktrace? (if *planck-integration-tests*
                               false
                               include-stacktrace?)
         message             (if (instance? ExceptionInfo error)
                               (ex-message error)
                               (.-message error))]
     (when (or (not ((fnil string/starts-with? "") printed-message message))
               include-stacktrace?)
       (println (((if roa? :rdr-ann-err-fn :ex-msg-fn) theme)
                 (str message (when (reader-error? error)
                                (location-info error))))))
     (when-let [data (and print-ex-data? (ex-data error))]
       (print-value data {::as-code? false}))
     (when include-stacktrace?
       (load-core-macros-source-maps!)
       (let [canonical-stacktrace (->> (st/parse-stacktrace
                                         {}
                                         (.-stack error)
                                         {:ua-product :safari}
                                         {:output-dir "file://(/goog/..)?"})
                                    (drop-while #(string/starts-with? (:function %) "PLANCK_"))
                                    (take-while #(not (stack-truncation-functions (:function %)))))]
         (load-bundled-source-maps! (distinct (map file->ns-sym (keep :file canonical-stacktrace))))
         (println
           ((:ex-stack-fn theme)
            (mapped-stacktrace-str
              canonical-stacktrace
              (or (:source-maps @planck.repl/st) {})
              nil)))))
     (when-let [cause (.-cause error)]
       (recur cause include-stacktrace? message)))))

(defn- get-macro-var
  [env sym macros-ns]
  {:pre [(symbol? macros-ns)]}
  (when-let [macro-var (with-compiler-env st
                         (resolve-var env (symbol macros-ns (name sym))))]
    (assoc macro-var :ns macros-ns)))

(defn- get-var
  [env sym]
  (binding [ana/*cljs-warning-handlers* nil]
    (let [var (or (with-compiler-env st (resolve-var env sym))
                  (when-let [macros-ns (sym (get-in @st [::ana/namespaces @current-ns :use-macros]))]
                    (get-macro-var env sym macros-ns)))]
      (when var
        (-> (cond-> var
              (not (:ns var))
              (assoc :ns (symbol (namespace (:name var))))
              (= (namespace (:name var)) (str (:ns var)))
              (update :name #(symbol (name %))))
          (update :ns (comp symbol drop-macros-suffix str)))))))

(defn- get-file-source
  [filepath]
  (if (symbol? filepath)
    (let [without-extension (string/replace
                              (string/replace (name filepath) #"\." "/")
                              #"-" "_")]
      (or
       (first (js/PLANCK_LOAD (str without-extension ".clj")))
       (first (js/PLANCK_LOAD (str without-extension ".cljc")))
       (first (js/PLANCK_LOAD (str without-extension ".cljs")))))
    (or
     (first (js/PLANCK_LOAD filepath))
     (first (js/PLANCK_READ_FILE filepath))
     (first (js/PLANCK_LOAD (string/replace filepath #"^out/" "")))
     (first (js/PLANCK_LOAD (string/replace filepath #"^src/" "")))
     (first (js/PLANCK_LOAD (string/replace filepath #"^/.*/planck-cljs/src/" ""))))))

(defn- fetch-source
  [var]
  (or (::repl-entered-source var)
      (when-let [filepath (or (:file var) (:file (:meta var)))]
        (when-let [file-source (get-file-source filepath)]
          (let [rdr (rt/source-logging-push-back-reader file-source)]
            (dotimes [_ (dec (:line var))] (rt/read-line rdr))
            (binding [r/*alias-map* (reify ILookup (-lookup [_ k] k))]
              (-> (r/read {:read-cond :allow :features #{:cljs}} rdr)
                meta :source)))))))

(defn- run-sync!
  "Like cljs.js/run-async!, but with the expectation that cb will be called
  synchronously within proc. When callbacks are done synchronously, run-async!
  ends up growing the stack as coll is processed, while this implementation
  employs recur."
  [proc coll break? cb]
  (loop [coll coll]
    (if (seq coll)
      (let [cb-val (atom nil)]
        (proc (first coll) #(reset! cb-val %))
        (if (break? @cb-val)
          (cb @cb-val)
          (recur (rest coll))))
      (cb nil))))

; Monkey-patch cljs.js/run-async! to instead be our more stack-efficient run-sync!
(set! ^:private-var-access-nowarn cljs/run-async! run-sync!)

(defn- process-deps
  [names opts cb]
  (run-sync! (fn [name cb]
               (cljs/require {:*compiler* st
                              :*cljs-dep-set* ana/*cljs-dep-set*}
                 name opts cb))
    names
    :error
    cb))

(defn- process-macros-deps
  [cache cb]
  (process-deps (distinct (vals (:require-macros cache))) {:macros-ns true} cb))

(defn- process-libs-deps
  [cache cb]
  (process-deps (distinct (concat (vals (:requires cache)) (vals (:imports cache)))) {} cb))

(declare ^{:arglists '([source opts])} execute-source)

(defn- with-load-domain
  [file]
  (cond
    (string/starts-with? file "@")
    {:file (subs file 1)
     :load-domain :classpath}

    (string/starts-with? file "@/")
    {:file (subs file 2)
     :load-domain :classpath}

    :else
    {:file file
     :load-domain :filesystem}))

(defn- process-execute-path
  [file opts]
  (binding [theme (assoc theme :err-font (:verbose-font theme))]
    (load-fn (with-load-domain file)
      (fn [{:keys [lang source source-url cache]}]
        (if source
          (case lang
            :clj (execute-source ["text" source] opts)
            :js (process-macros-deps cache
                  (fn [res]
                    (if-let [error (:error res)]
                      (handle-error (js/Error. error) false)
                      (process-libs-deps cache
                        (fn [res]
                          (if-let [error (:error res)]
                            (handle-error (js/Error. error) false)
                            (do
                              (js-eval source source-url)
                              (when-some [ns (:name cache)]
                                (swap! st assoc-in [::ana/namespaces ns] cache))))))))))
          (handle-error (js/Error. (str "Could not load file " file)) false))))))

(defn- resolve-ns
  "Resolves a namespace symbol to a namespace by first checking to see if it
  is a namespace alias."
  [ns-sym]
  (or (get-in @st [::ana/namespaces ana/*cljs-ns* :requires ns-sym])
      (get-in @st [::ana/namespaces ana/*cljs-ns* :require-macros ns-sym])
      ns-sym))

(defn dir*
  [nsname]
  (let [ns (resolve-ns nsname)]
    (run! prn
      (distinct (sort (concat
                        (public-syms ns)
                        (public-syms (add-macros-suffix ns))))))))

(defn apropos*
  [str-or-pattern]
  (let [matches? (if (instance? js/RegExp str-or-pattern)
                   #(re-find str-or-pattern (str %))
                   #(string/includes? (str %) (str str-or-pattern)))]
    (distinct (sort (mapcat (fn [ns]
                              (let [ns-name (drop-macros-suffix (str ns))]
                                (map #(symbol ns-name (str %))
                                  (filter matches? (public-syms ns)))))
                      (all-ns))))))

(defn- undo-reader-conditional-whitespace-docstring
  "Undoes the effect that wrapping a reader conditional around a defn has on a
  docstring."
  [s]
  ;; We look for five spaces (or six, in case that the docstring
  ;; is not aligned under the first quote) after the first newline
  ;; (or two, in case the doctring has an unpadded blank line
  ;; after the first), and then replace all five (or six) spaces
  ;; after newlines with two.
  (when-not (nil? s)
    (if (re-find #"[^\n]*\n\n?      ?\S.*" s)
      (string/replace s #"\n      ?" "\n  ")
      s)))

(defn- str-butlast
  [s]
  (subs s 0 (dec (count s))))

(defn- format-spec
  [spec left-margin ns]
  (let [raw-print (with-out-str (print-value (s/describe spec)
                                  {::keyword-ns     ns
                                   ::spec?          true
                                   ::as-code?       true
                                   ::term-width-adj (- left-margin)}))]
    (string/replace (str-butlast raw-print) #"\n"
      (apply str \newline (repeat left-margin " ")))))

(defn- print-doc [{n :ns nm :name :as m}]
  (println "-------------------------")
  (if-let [spec (:spec m)]
    (print-value spec {})
    (println (str (when-let [ns (:ns m)] (str ns "/")) (:name m))))
  (when (:protocol m)
    (println "Protocol"))
  (cond
    (:forms m) (doseq [f (:forms m)]
                 (println "  " f))
    (:arglists m) (let [arglists (:arglists m)]
                    (if (or (:macro m)
                            (:repl-special-function m))
                      (prn arglists)
                      (prn
                        (if (= 'quote (first arglists))
                          (second arglists)
                          arglists)))))
  (if (:special-form m)
    (do
      (println "Special Form")
      (println " " (:doc m))
      (if (contains? m :url)
        (when (:url m)
          (println (str "\n  Please see http://clojure.org/" (:url m))))
        (println (str "\n  Please see http://clojure.org/special_forms#"
                   (:name m)))))
    (do
      (when (:macro m)
        (println "Macro"))
      (when (:spec m)
        (println "Spec"))
      (when (:repl-special-function m)
        (println "REPL Special Function"))
      (println " " (:doc m))
      (when (:protocol m)
        (doseq [[name {:keys [doc arglists]}] (:methods m)]
          (println)
          (println " " name)
          (println " " arglists)
          (when doc
            (println " " doc))))
      (when n
        (let [spec-lookup (fn [ns-suffix]
                            (s/get-spec (symbol (str (ns-name n) ns-suffix) (name nm))))]
          (when-let [fnspec (or (spec-lookup "")
                                (spec-lookup "$macros"))]
            (print "Spec")
            (doseq [role [:args :ret :fn]]
              (when-let [spec (get fnspec role)]
                (print (str "\n " (name role) ":") (format-spec spec (+ 3 (count (name role))) n))))
            (println)))))))

(defn doc*
  [name]
  (if-let [special-name ('{&       fn
                           catch   try
                           finally try} name)]
    (doc* special-name)
    (cond

      (special-doc-map name)
      (print-doc (special-doc name))

      (repl-special-doc-map name)
      (print-doc (repl-special-doc name))

      (qualified-keyword? name)
      (print-doc {:spec name :doc (format-spec (s/get-spec name) 3 (symbol (namespace name)))})

      (get-namespace name)
      (print-doc
        (select-keys (get-namespace name) [:name :doc]))

      (get-var (get-aenv) name)
      (print-doc
        (let [var (get-var (get-aenv) name)
              var (assoc var :forms (-> var :meta :forms second)
                             :arglists (-> var :meta :arglists second))
              m   (select-keys var
                    [:ns :name :doc :forms :arglists :macro :url])
              m   (update m :doc undo-reader-conditional-whitespace-docstring)]
          (cond-> (update-in m [:name] cljs.core/name)
            (:protocol-symbol var)
            (assoc :protocol true
                   :methods
                   (->> (get-in var [:protocol-info :methods])
                     (map (fn [[fname sigs]]
                            [fname {:doc      (:doc
                                               (get-var (get-aenv)
                                                 (symbol (str (:ns var)) (str fname))))
                                    :arglists (seq sigs)}]))
                     (into {})))))))))

(defn- namespace-doc [nspace]
  (select-keys (get-in @st [::ana/namespaces nspace]) [:name :doc]))

(defn find-doc*
  [re-string-or-pattern]
  (let [re (re-pattern re-string-or-pattern)
        ms (concat (mapcat #(sort-by :name
                              (map (fn [[k v]]
                                     (assoc (:meta v) :name (symbol % k)))
                                (get-in @st [::ana/namespaces % :defs])))
                     (all-ns))
             (map namespace-doc (all-ns))
             (map special-doc (keys special-doc-map)))]
    (doseq [m ms
            :when (and (:doc m)
                       (or (re-find re (:doc m))
                           (re-find re (str (:name m)))))]
      (doc* (:name m)))))

(defn source*
  [sym]
  (println (or (fetch-source (get-var (get-aenv) sym))
               "Source not found")))

(defn pst*
  ([]
   (pst* '*e))
  ([expr]
   (try (cljs/eval st
          expr
          (make-base-eval-opts)
          (fn [{:keys [value]}]
            (when value
              (print-error value :pst))))
        (catch js/Error e (prn :caught e)))))

(defn- process-load-file
  [argument opts]
  (let [filename argument]
    (try
      (execute-source ["path" filename] opts)
      (catch :default e
        (handle-error e false)))))

(defn- root-resource
  "Returns the root directory path for a lib"
  [lib]
  (str \/
    (-> (name lib)
      (string/replace \- \_)
      (string/replace \. \/))))

(defn- root-directory
  "Returns the root resource path for a lib"
  [lib]
  (let [d (root-resource lib)]
    (subs d 0 (.lastIndexOf d "/"))))

(defn- load-path->cp-path
  [path]
  (let [src (if (= "/" (first path))
              path
              (str (root-directory @current-ns) \/ path))
        src (.substring src 1)]
    (or (and (first (js/PLANCK_LOAD (str src ".cljs"))) (str "@" src ".cljs"))
        (str "@" src ".cljc"))))

(defn- process-load
  [paths opts]
  (let [cp-paths (map load-path->cp-path paths)]
    (run! #(process-execute-path % opts) cp-paths)))

(defn- process-repl-special
  [expression-form {:keys [print-nil-expression?] :as opts}]
  (let [argument (second expression-form)]
    (case (first expression-form)
      in-ns (process-in-ns argument)
      load-file (process-load-file argument (assoc opts :expression? false))
      load (process-load (rest expression-form) (assoc opts :expression? false)))
    (when print-nil-expression?
      (println (str (:results-font theme) "nil" (:reset-font theme))))))

;; Clojure REPLs bind thread-local state for a REPL so that set! calls are isolated.
;; We instead employ a "context switching" strategy given the behavior of dynamic
;; vars in ClojureScript.

(defn- capture-session-state
  "Captures all of the commonly set global vars as a session state map."
  []
  {:*print-meta*           *print-meta*
   :*print-length*         *print-length*
   :*print-level*          *print-level*
   :*print-namespace-maps* *print-namespace-maps*
   :*unchecked-if*         *unchecked-if*
   :*assert*               *assert*
   :*1                     *1
   :*2                     *2
   :*3                     *3
   :*e                     *e})

(defn- set-session-state
  "Sets the session state given a sesssion state map."
  [session-state]
  (set! *print-meta* (:*print-meta* session-state))
  (set! *print-length* (:*print-length* session-state))
  (set! *print-level* (:*print-level* session-state))
  (set! *print-namespace-maps* (:*print-namespace-maps* session-state))
  (set! *unchecked-if* (:*unchecked-if* session-state))
  (set! *assert* (:*assert* session-state))
  (set! *1 (:*1 session-state))
  (set! *2 (:*2 session-state))
  (set! *3 (:*3 session-state))
  (set! *e (:*e session-state)))

(def ^{:private true
       :doc     "The default state used to initialize a new REPL session."} default-session-state
  (atom (capture-session-state)))

(defonce ^{:private true
           :doc     "The state for each session, keyed by session ID."} session-states (atom {}))

(defn- ^:export clear-state-for-session
  "Clears the session state for a completed session."
  [session-id]
  (swap! session-states dissoc session-id))

(defn- set-session-state-for-session-id
  "Sets the session state for a given session."
  [session-id]
  (set-session-state (get @session-states session-id @default-session-state)))

(defn- capture-session-state-for-session-id
  "Captures the session state for a given session."
  [session-id]
  (swap! session-states assoc session-id (capture-session-state)))

(defn- process-1-2-3
  [expression-form value]
  (when-not
   (or ('#{*1 *2 *3 *e} expression-form)
       (ns-form? expression-form))
    (set! *3 *2)
    (set! *2 *1)
    (set! *1 value)))

(defn- cache-source-fn
  [source-text]
  (fn [x cb]
    (when (:source x)
      (let [x (cond-> x (compile?) compile)
            [file-namespace relpath] (extract-cache-metadata-mem source-text)
            cache  (get-namespace file-namespace)]
        (write-cache relpath file-namespace (:source x) cache)))
    (cb {:value nil})))

(defn- print-value
  [value opts]
  (if *pprint-results*
    (if-let [[term-height term-width] (js/PLANCK_GET_TERM_SIZE)]
      ((planck.pprint.width-adjust/wrap
         (if (::as-code? opts)
           planck.pprint.code/pprint
           planck.pprint.data/pprint))
       value {:width       ((fnil + 0 0) term-width (::term-width-adj opts))
              :theme       theme
              :spec?       (::spec? opts)
              :keyword-ns  (::keyword-ns opts)
              :no-newline? true})
      (pr value))
    (pr value))
  (when-not (::no-newline? opts)
    (newline)))

(s/def ::as-code? boolean?)
(s/def ::spec? boolean?)
(s/def ::keyword-ns symbol?)
(s/def ::term-width-adj integer?)
#_(s/fdef print-value
    :args (s/cat :value ::s/any :opts (s/keys :opt [::as-code? ::term-width-adj ::spec ::keyword-ns])))

(defn- wrap-warning-font
  [s]
  (str (:err-font theme) s (str (:reset-font theme))))

(defn- warning-handler [warning-type env extra]
  (let [warning-string (with-err-str
                         (ana/default-warning-handler warning-type env
                           (update extra :js-op eliminate-macros-suffix)))]
    (binding [*print-fn* *print-err-fn*]
      (when-not (empty? warning-string)
        (when-let [column (:column env)]
          (when (show-indicator?)
            (println (wrap-warning-font (form-indicator column @current-ns)))))
        (print (wrap-warning-font warning-string))))))

(defn- call-form?
  [expression-form allowed-operators]
  (contains? allowed-operators (and (list? expression-form)
                                    (first expression-form))))

(defn- macroexpand-form?
  "Determines if the expression is a macroexpansion expression."
  [expression-form]
  (call-form? expression-form '#{macroexpand macroexpand-1}))

(defn- load-form?
  "Determines if the expression is a form that loads code."
  [expression-form]
  (call-form? expression-form '#{require require-macros import
                                 cljs.core/require cljs.core/require-macros cljs.core/import
                                 clojure.core/require clojure.core/require-macros clojure.core/import
                                 ns load load-file}))

(defn- def-form?
  "Determines if the expression is a def expression which returns a Var."
  [expression-form]
  (call-form? expression-form '#{def defn defn- defonce defmulti defmacro}))

(defn- process-execute-source
  [source-text expression-form
   {:keys [expression? print-nil-expression? include-stacktrace? source-path session-id] :as opts}]
  (try
    (set-session-state-for-session-id session-id)
    (let [initial-ns @current-ns]
      (binding [ana/*cljs-warning-handlers* (if expression?
                                              [warning-handler]
                                              [ana/default-warning-handler])]
        (when (and expression? (load-form? expression-form))
          (disable-error-indicator!))
        (cljs/eval-str
          st
          source-text
          (if expression?
            expression-name
            (or source-path "File"))
          (merge
            {:ns initial-ns}
            (select-keys @app-env [:verbose :checked-arrays :static-fns :fn-invoke-direct])
            (if expression?
              (merge {:context       :expr
                      :def-emits-var (-> @app-env :opts (:def-emits-var true))}
                (when (load-form? expression-form)
                  {:source-map (source-map?)}))
              (merge {:source-map (source-map?)}
                (when (:cache-path @app-env)
                  {:cache-source (cache-source-fn source-text)}))))
          (fn [{:keys [ns value error] :as ret}]
            (if expression?
              (when-not error
                (when (or print-nil-expression?
                          (not (nil? value)))
                  (print-value value {::as-code? (macroexpand-form? expression-form)}))
                (process-1-2-3 expression-form value)
                (when (def-form? expression-form)
                  (let [{:keys [ns name]} (meta value)]
                    (swap! st assoc-in [::ana/namespaces ns :defs name ::repl-entered-source] source-text)))
                (reset! current-ns ns)
                nil))
            (when error
              (handle-error error include-stacktrace?))))))
    (catch :default e
      (handle-error e include-stacktrace?))
    (finally (capture-session-state-for-session-id session-id))))

(defn- execute-source
  [source opts]
  (let [[source-type source-value] source
        {:keys [expression?]} opts]
    (binding [ana/*cljs-ns*    @current-ns
              *ns*             (create-ns @current-ns)
              cljs/*load-fn*   load-fn
              cljs/*eval-fn*   (get-eval-fn)
              tags/*cljs-data-readers* (data-readers)]
      (if-not (= "text" source-type)
        (process-execute-path source-value (assoc opts :source-path source-value))
        (let [source-text source-value
              first-form  (eof-guarded-read source-text)]
          (when (not= eof first-form)
            (let [expression-form (and expression? first-form)]
              (if (repl-special? expression-form)
                (process-repl-special expression-form opts)
                (process-execute-source source-text expression-form opts)))))))))

(defn- ^:export execute
  [source expression? print-nil-expression? set-ns theme-id session-id]
  (reset-show-indicator!)
  (when set-ns
    (reset! current-ns (symbol set-ns)))
  (binding [theme (get-theme (keyword theme-id))]
    (try
      (execute-source source {:expression?           expression?
                              :print-nil-expression? print-nil-expression?
                              :include-stacktrace?   true
                              :session-id            session-id})
      (catch :default e
        (handle-error e true)))))

(defn- eval
  ([form]
   (eval form (.-name *ns*)))
  ([form ns]
   (let [result (atom nil)]
     (cljs/eval st form
       {:ns            ns
        :context       :expr
        :def-emits-var true}
       (fn [{:keys [value error]}]
         (if error
           (handle-error error true)
           (reset! result value))))
     @result)))

(defn- ns-resolve
  [ns sym]
  (let [result (atom nil)]
    (binding [ana/*cljs-warnings* (zipmap (keys ana/*cljs-warnings*) (repeat false))]
      (cljs/eval st `(~'var ~sym)
        {:ns      ns
         :context :expr}
        (fn [{:keys [value error]}]
          (when-not error
            (reset! result value)))))
    @result))

(defn- resolve
  [sym]
  (ns-resolve (.-name *ns*) sym))

(defn get-arglists
  "Return the argument lists for the given symbol as string, or nil if not
  found."
  [s]
  (try
    (when-let [var (some->> s repl-read-string first (resolve-var (assoc @env/*compiler* :ns (ana/get-namespace ana/*cljs-ns*))))]
      (let [arglists (if-not (or (:macro var))
                       (:arglists var)
                       (-> var :meta :arglists second))]
        (if (= 'quote (first arglists))
          (second arglists)
          arglists)))
    (catch :default _
      nil)))

(defn- intern
  ([ns name]
   (when-let [the-ns (find-ns (cond-> ns (instance? Namespace ns) ns-name))]
     (eval `(def ~name) (ns-name the-ns))))
  ([ns name val]
   (when-let [the-ns (find-ns (cond-> ns (instance? Namespace ns) ns-name))]
     (eval `(def ~name ~val) (ns-name the-ns)))))

(defn- ^:export wrap-color-err
  []
  (let [orig-print-err-fn js/PLANCK_PRINT_ERR_FN]
    (set! js/PLANCK_PRINT_ERR_FN
      (fn [msg]
        (orig-print-err-fn (:err-font theme))
        (orig-print-err-fn msg)
        (orig-print-err-fn (:reset-font theme))))))

(defn- ^:export init-data-readers []
  (binding [cljs/*eval-fn* (fn [{:keys [source]}]
                             (js-eval source nil))]
    (set! *data-readers* (get-data-readers))))

(defn- ^:export maybe-load-user-file []
  (let [try-load (fn [name]
                   (when-let [[_ _ path origin] (js/PLANCK_LOAD name)]
                     (when (= "src" origin)
                       (process-load-file path {:expression? false})
                       true)))]
    (or (try-load "user.cljs")
        (try-load "user.cljc"))))
