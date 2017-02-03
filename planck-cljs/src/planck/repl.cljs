(ns planck.repl
  (:require-macros
    [cljs.env.macros :refer [with-compiler-env]]
    [planck.repl :refer [with-err-str]])
  (:require
    [cljs.analyzer :as ana]
    [cljs.compiler :as comp]
    [cljs.env :as env]
    [cljs.js :as cljs]
    [cljs.source-map :as sm]
    [cljs.spec :as s]
    [cljs.stacktrace :as st]
    [cljs.tagged-literals :as tags]
    [cljs.tools.reader :as r]
    [cljs.tools.reader.reader-types :as rt]
    [cljsjs.parinfer]
    [clojure.string :as string]
    [cognitect.transit :as transit]
    [goog.string :as gstring]
    [lazy-map.core :refer-macros [lazy-map]]
    [planck.js-deps :as js-deps]
    [planck.pprint.code]
    [planck.pprint.data]
    [planck.repl-resources :refer [special-doc-map repl-special-doc-map]]
    [planck.themes :refer [get-theme]]
    [tailrecursion.cljson :refer [cljson->clj]]))

#_(s/fdef planck.repl$macros/dir
    :args (s/cat :sym symbol?))

#_(s/fdef planck.repl$macros/doc
    :args (s/cat :sym symbol?))

#_(s/fdef planck.repl$macros/find-doc
    :args (s/cat :re-string-or-pattern (s/or :string string? :regexp regexp?)))

#_(s/fdef planck.repl$macros/source
    :args (s/cat :sym symbol?))

(def ^{:dynamic true
       :doc     "*pprint-results* controls whether Planck REPL results are
  pretty printed. If it is bound to logical false, results
  are printed in a plain fashion. Otherwise, results are
  pretty printed."}
*pprint-results* true)

(def ^:private expression-name "Expression")
(def ^:private could-not-eval-expr (str "Could not eval " expression-name))

(defn- calc-x-line [text pos line]
  (let [x (string/index-of text "\n")]
    (if (or (nil? x)
            (< pos (inc x)))
      {:cursorX    pos
       :cursorLine line}
      (recur (subs text (inc x)) (- pos (inc x)) (inc line)))))

(defn- ^:export indent-space-count
  "Given text representing a partially entered form,
  returns the number of spaces to indent a newly entered
  line. Returns 0 if unsuccessful."
  [text]
  (let [pos (count text)
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
            theme (assoc theme :err-font (:verbose-font theme))]
    (apply println args)))

(declare print-error)
(declare handle-error)

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

(defn- load-core-analysis-cache
  [eager ns-sym file-prefix]
  (let [keys [:rename-macros :renames :use-macros :excludes :name :imports :requires :uses :defs :require-macros :cljs.analyzer/constants :doc]
        load (fn [key]
               (transit-json->cljs (first (js/PLANCK_LOAD (str file-prefix (munge key) ".json")))))]
    (cljs/load-analysis-cache! st ns-sym
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

(defn- ^:export init
  [repl verbose cache-path static-fns elide-asserts]
  (load-core-analysis-caches repl)
  (let [opts (or (read-opts-from-file "opts.clj")
                 {})]
    (reset! planck.repl/app-env (merge {:repl       repl
                                        :verbose    verbose
                                        :cache-path cache-path
                                        :opts       opts}
                                  (when static-fns
                                    {:static-fns true})))
    (js-deps/index-foreign-libs opts)
    (js-deps/index-upstream-foreign-libs))
  (setup-asserts elide-asserts))

(defn- read-chars
  [reader]
  (lazy-seq
    (when-let [ch (rt/read-char reader)]
      (cons ch (read-chars reader)))))

(defn- repl-read-string
  "Returns a vector of the first read form, and any balance text."
  [source]
  (binding [ana/*cljs-ns* @current-ns
            *ns* (create-ns @current-ns)
            env/*compiler* st
            r/*data-readers* tags/*cljs-data-readers*
            r/resolve-symbol ana/resolve-symbol
            r/*alias-map* (current-alias-map)]
    (let [reader (rt/string-push-back-reader source)]
      [(r/read {:read-cond :allow :features #{:cljs}} reader) (apply str (read-chars reader))])))

(defn- eof-while-reading?
  [message]
  (or
    (= "EOF while reading" message)
    (= "EOF while reading string" message)))

(defn- ^:export is-readable?
  "Returns a string representing any text after the first readible form,
  nor nil if nothing readible."
  [source theme-id]
  (try
    (second (repl-read-string source))
    (catch :default e
      (let [message (.-message e)]
        (cond
          (eof-while-reading? message) nil
          (= "EOF" message) ""
          :else (binding [theme (get-theme (keyword theme-id))]
                  (print-error e false)
                  ""))))))

(defn- ns-form?
  [form]
  (and (seq? form) (= 'ns (first form))))

(defn- extract-namespace
  [source]
  (let [first-form (first (repl-read-string source))]
    (when (ns-form? first-form)
      (second first-form))))

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

(defn- make-base-eval-opts
  []
  {:ns         @current-ns
   :context    :expr
   :verbose    (:verbose @app-env)
   :static-fns (:static-fns @app-env)
   :source-map true})

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
  (map (comp str key)
    (into []
      (comp
        (filter (if allow-private?
                  identity
                  #(not (:private (val %)))))
        (remove #(:anonymous (val %))))
      (apply merge
        ((juxt :defs :macros)
          (get-namespace ns-sym))))))

(defn- is-completion?
  [buffer-match-suffix candidate]
  (re-find (js/RegExp. (str "^" buffer-match-suffix)) candidate))

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
    planck.js-deps
    planck.repl
    planck.repl-resources
    planck.themes
    tailrecursion.cljson
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
    clojure.test
    clojure.spec
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
  "Expand the typed namespace symbol to a known namespace, consulting
  current namespace aliases if necessary."
  [typed-ns]
  (or (get-in st [:cljs.analyzer/namespaces typed-ns :name])
      (typed-ns (current-alias-map))
      typed-ns))

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
           (completion-candidates-for-ns @current-ns true)
           (when top-form?
             (concat
               (map str (keys special-doc-map))
               (map str (keys repl-special-doc-map))))))))

(defn- spec-registered-keywords
  [ns]
  (->> (s/registry)
    keys
    (filter keyword?)
    (filter #(= (str ns) (namespace %)))))

(defn- local-keyword-str
  [kw]
  (str "::" (name kw)))

(defn- local-keyword
  "Returns foo for ::foo, otherwise nil"
  [buffer]
  (second (re-find #"::([a-zA-Z-]*)$" buffer)))

(defn- local-keyword-completions
  [buffer kw-name]
  (let [buffer-prefix (subs buffer 0 (- (count buffer) (count kw-name) 2))]
    (clj->js (->> (spec-registered-keywords @current-ns)
               (map local-keyword-str)
               (filter #(string/starts-with? % (str "::" kw-name)))
               (map #(str buffer-prefix %))))))

(defn- ^:export get-completions
  [buffer]
  (if-let [kw-name (local-keyword buffer)]
    (local-keyword-completions buffer kw-name)
    (let [top-form? (re-find #"^\s*\(\s*[^()\s]*$" buffer)
          typed-ns (second (re-find #"\(*(\b[a-zA-Z-.]+)/[a-zA-Z-]+$" buffer))]
      (let [buffer-match-suffix (re-find #":?[a-zA-Z-\.]*$" buffer)
            buffer-prefix (subs buffer 0 (- (count buffer) (count buffer-match-suffix)))]
        (clj->js (if (= "" buffer-match-suffix)
                   []
                   (map #(str buffer-prefix %)
                     (sort
                       (filter (partial is-completion? buffer-match-suffix)
                         (completion-candidates top-form? typed-ns))))))))))

(defn- is-completely-readable?
  [source]
  (let [rdr (rt/indexing-push-back-reader source 1 "noname")]
    (binding [ana/*cljs-ns* @current-ns
              *ns* (create-ns @current-ns)
              env/*compiler* st
              r/*data-readers* tags/*cljs-data-readers*
              r/resolve-symbol ana/resolve-symbol
              r/*alias-map* (current-alias-map)]
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
            first-char (subs candidate-form 0 1)]
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
  "Gets the highlight coordinates [line pos] for the previous matching
  brace. This is done by progressivly expanding source considered
  until a readable form is encountered with a matching brace on the
  other end. The coordinate system is such that line 0 is the current
  buffer line, line 1 is the previous line, and so on, and pos is the
  position in that line."
  [pos buffer previous-lines]
  (let [previous-lines (js->clj previous-lines)
        previous-source (string/join "\n" previous-lines)
        total-source (if (empty? previous-lines)
                       buffer
                       (str previous-source "\n" buffer))
        total-pos (+ (if (empty? previous-lines)
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
        relpath (cljs/ns->relpath file-namespace)]
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
    (binding [r/*data-readers* tags/*cljs-data-readers*]
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
            (select-keys @app-env [:static-fns]))]
    (if (empty? m)
      nil
      m)))

;; Hack to remember which file path each namespace was loaded from
(defonce ^:private name-path (atom {}))

(declare add-suffix)

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

(defn- write-cache
  [path name source cache]
  (when (and path source cache (:cache-path @app-env))
    (let [cache-json (cljs->transit-json cache)
          sourcemap-json (when-let [sm (get-in @planck.repl/st [:source-maps (:name cache)])]
                           (cljs->transit-json sm))]
      (log-cache-activity :write path cache-json sourcemap-json)
      (js/PLANCK_CACHE (cache-prefix-for-path path (is-macros? cache))
        (str (form-compiled-by-string (form-build-affecting-options)) "\n" source)
        cache-json
        sourcemap-json))))

(defn- js-eval
  [source source-url]
  (if source-url
    (let [exception (js/PLANCK_EVAL source source-url)]
      (when exception
        (throw exception)))
    (js/eval source)))

(defn- caching-js-eval
  [{:keys [path name source source-url cache]}]
  (when (and path source cache (:cache-path @app-env))
    (write-cache path name source cache))
  (let [source-url (or source-url
                       (when (and (not (empty? path))
                                  (not= expression-name path))
                         (file-url (js-path-for-name name))))]
    (js-eval source source-url)))

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
  (let [path (cond-> path
               macros (add-suffix "$macros"))
        cache-prefix (if (= :calculate-cache-prefix cache-prefix)
                       (cache-prefix-for-path (second (extract-cache-metadata-mem source)) macros)
                       cache-prefix)
        [js-source js-modified] (or (raw-load (add-suffix path ".js"))
                                    (js/PLANCK_READ_FILE (str cache-prefix ".js")))
        [cache-json _] (or (raw-load (str path ".cache.json"))
                           (js/PLANCK_READ_FILE (str cache-prefix ".cache.json")))
        [sourcemap-json _] (or (raw-load (str path ".js.map.json"))
                               (js/PLANCK_READ_FILE (str cache-prefix ".js.map.json")))]
    (when (cached-js-valid? js-source js-modified source-modified)
      (log-cache-activity :read path cache-json sourcemap-json)
      (when (and sourcemap-json name)
        (swap! st assoc-in [:source-maps name] (transit-json->cljs sourcemap-json)))
      (merge {:lang   :js
              :source ""}
        (when-not (skip-load-js? name)
          {:source     (cond-> js-source (not (bundled? js-modified source-modified)) strip-first-line)
           :source-url (file-url (add-suffix path ".js"))})
        (when cache-json
          {:cache (transit-json->cljs cache-json)})))))

(declare inject-planck-eval)

(defn- load-and-callback!
  [name path macros lang cache-prefix cb]
  (let [[raw-load [source modified loaded-path]] [js/PLANCK_LOAD (js/PLANCK_LOAD path)]
        [raw-load [source modified loaded-path]] (if source
                                                   [raw-load [source modified loaded-path]]
                                                   [js/PLANCK_READ_FILE (js/PLANCK_READ_FILE path) path])]
    (when source
      (when name
        (swap! name-path assoc name path))
      (cb (merge
            {:lang   lang
             :source source
             :file   loaded-path}
            (when-not (= :js lang)
              (cached-callback-data name path macros cache-prefix source modified raw-load))))
      (when (and (= name 'cljs.spec.test) macros)
        (inject-planck-eval 'cljs.spec.test$macros))
      :loaded)))

(defn- closure-index
  []
  (let [paths-to-provides
        (map (fn [[_ path provides]]
               [path (map second
                       (re-seq #"'(.*?)'" provides))])
          (re-seq #"\ngoog\.addDependency\('(.*)', \[(.*?)\].*"
            (first (js/PLANCK_LOAD "goog/deps.js"))))]
    (into {}
      (for [[path provides] paths-to-provides
            provide provides]
        [(symbol provide) (str "goog/" (second (re-find #"(.*)\.js$" path)))]))))

(def ^:private closure-index-mem (memoize closure-index))

(defn- skip-load?
  [{:keys [name macros]}]
  (or
    (= name 'cljsjs.parinfer)
    (= name 'cljs.core)
    (and (= name 'clojure.core.rrb-vector.macros) macros)
    (and (= name 'cljs.env.macros) macros)
    (and (= name 'cljs.analyzer.macros) macros)
    (and (= name 'cljs.compiler.macros) macros)
    (and (= name 'cljs.js) macros)
    (and (= name 'cljs.pprint) macros)
    (and (= name 'cljs.reader) macros)
    (and (= name 'cljs.tools.reader.reader-types) macros)
    (and (= name 'tailrecursion.cljson) macros)
    (and (= name 'lazy-map.core) macros)))

(defn- do-load-file
  [file cb]
  (when-not (load-and-callback! nil file false :clj :calculate-cache-prefix cb)
    (cb nil)))

(defonce ^:private foreign-files-loaded (atom #{}))

(defn- not-yet-loaded
  "Determines the files not yet loaded, consulting and augmenting
  foreign-files-loaded."
  [files-to-load]
  (let [result (remove @foreign-files-loaded files-to-load)]
    (swap! foreign-files-loaded into result)
    result))

(defn- file-content
  "Loads the content for a given file."
  [file]
  (first (or (js/PLANCK_READ_FILE file)
             (js/PLANCK_LOAD file))))

(defn- do-load-foreign
  [name cb]
  (let [files-to-load (js-deps/files-to-load name)
        _ (when (:verbose @app-env)
            (println "Loading foreign libs files:" files-to-load))
        sources (map file-content (not-yet-loaded files-to-load))]
    (cb {:lang   :js
         :source (string/join "\n" sources)})))

;; Represents code for which the goog JS is already loaded
(defn- skip-load-goog-js?
  [name]
  ('#{goog.object
      goog.string
      goog.string.StringBuffer
      goog.math.Long} name))

(defn- do-load-goog
  [name cb]
  (if (skip-load-goog-js? name)
    (cb {:lang   :js
         :source ""})
    (if-let [goog-path (get (closure-index-mem) name)]
      (when-not (load-and-callback! name (str goog-path ".js") false :js nil cb)
        (cb nil))
      (cb nil))))

(defn- do-load-other
  [name path macros cb]
  (loop [extensions (if macros
                      [".clj" ".cljc"]
                      [".cljs" ".cljc" ".js"])]
    (if extensions
      (when-not (load-and-callback!
                  name
                  (str path (first extensions))
                  macros
                  (extension->lang (first extensions))
                  (cache-prefix-for-path path macros)
                  cb)
        (recur (next extensions)))
      (cb nil))))

; file here is an alternate parameter denoting a filesystem path
(defn- load
  [{:keys [name macros path file] :as full} cb]
  (cond
    (skip-load? full) (cb {:lang   :js
                           :source ""})
    file (do-load-file file cb)
    (name @js-deps/foreign-libs-index) (do-load-foreign name cb)
    (re-matches #"^goog/.*" path) (do-load-goog name cb)
    :else (do-load-other name path macros cb)))

(declare skip-cljsjs-eval-error)

(defn- handle-error
  [e include-stacktrace?]
  (let [cause (or (.-cause e) e)
        is-planck-exit-exception? (= "PLANCK_EXIT" (.-message cause))]
    (when-not is-planck-exit-exception?
      (print-error e include-stacktrace?))
    (if (and (not is-planck-exit-exception?) (not (:repl @app-env)))
      (js/PLANCK_SET_EXIT_VALUE 1)
      (set! *e (skip-cljsjs-eval-error e)))))

(defn- ^:export run-main
  [main-ns & args]
  (let [main-args (js->clj args)
        opts (make-base-eval-opts)]
    (binding [cljs/*load-fn* load
              cljs/*eval-fn* caching-js-eval]
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
                (try
                  (apply value main-args)
                  (catch :default e
                    (handle-error e true)))))))))
    nil))

(defn- load-core-source-maps!
  []
  (when-not (get (:source-maps @planck.repl/st) 'cljs.core)
    (swap! st update-in [:source-maps] merge {'cljs.core
                                              (sm/decode
                                                (cljson->clj
                                                  (first (js/PLANCK_LOAD "cljs/core.js.map"))))
                                              'cljs.core$macros
                                              (sm/decode
                                                (cljson->clj
                                                  (first (js/PLANCK_LOAD "core$macros.js.map"))))})))

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

(defn- reader-or-analysis?
  "Indicates if an exception is a reader or analysis exception."
  [e]
  (and (instance? ExceptionInfo e)
       (some #{[:type :reader-exception] [:tag :cljs/analysis-error]} (ex-data e))))

(defn- form-demunge-map
  "Forms a map from munged function symbols (as they appear in stacktraces)
  to their unmunged forms."
  [ns]
  {:pre [(symbol? ns)]}
  (let [ns-str (str ns)
        munged-ns-str (string/replace ns-str #"\." "$")]
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

(defn- gensym?
  [sym]
  (string/starts-with? (name sym) "G__"))

(defn- demunge-sym
  [munged-sym]
  (let [demunge-maps (cons @core-demunge-map (non-core-demunge-maps))]
    (str (or (lookup-sym demunge-maps munged-sym)
             (demunge-protocol-fn demunge-maps munged-sym)
             (demunge-local demunge-maps munged-sym)
           (if (gensym? munged-sym)
             munged-sym
             (demunge munged-sym))))))

(defn- mapped-stacktrace-str
  ([stacktrace sms]
   (mapped-stacktrace-str stacktrace sms nil))
  ([stacktrace sms opts]
   (apply str
     (for [{:keys [function file line column]} (st/mapped-stacktrace stacktrace sms opts)
           :let [demunged (str (when function (demunge-sym function)))]
           :when (not= demunged "cljs.core/-invoke [cljs.core/IFn]")]
       (str \tab demunged " (" file (when line (str ":" line))
         (when column (str ":" column)) ")" \newline)))))

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

(declare print-value)

(defn- print-error
  ([error]
   (print-error error true))
  ([error include-stacktrace?]
   (print-error error include-stacktrace? nil))
  ([error include-stacktrace? printed-message]
   (print-error-column-indicator error)
   (let [error (skip-cljsjs-eval-error error)
         roa? (reader-or-analysis? error)
         print-ex-data? (= include-stacktrace? :pst)
         include-stacktrace? (or (= include-stacktrace? :pst)
                                 (and include-stacktrace?
                                      (not roa?)))
         include-stacktrace? (if *planck-integration-tests*
                               false
                               include-stacktrace?)
         message (if (instance? ExceptionInfo error)
                   (ex-message error)
                   (.-message error))]
     (when (or (not ((fnil string/starts-with? "") printed-message message))
               include-stacktrace?)
       (println (((if roa? :rdr-ann-err-fn :ex-msg-fn) theme) message)))
     (when-let [data (and print-ex-data? (ex-data error))]
       (print-value data {::as-code? false}))
     (when include-stacktrace?
       (load-core-source-maps!)
       (let [canonical-stacktrace (st/parse-stacktrace
                                    {}
                                    (.-stack error)
                                    {:ua-product :safari}
                                    {:output-dir "file://(/goog/..)?"})]
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

(defn- all-macros-ns
  []
  (->> (all-ns)
    (filter #(string/ends-with? (str %) "$macros"))))

(defn- get-var
  [env sym]
  (binding [ana/*cljs-warning-handlers* nil]
    (let [var (or (with-compiler-env st (resolve-var env sym))
                  (some #(get-macro-var env sym %) (all-macros-ns)))]
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
            (-> (r/read {:read-cond :allow :features #{:cljs}} rdr)
              meta :source))))))

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
(set! cljs/run-async! run-sync!)

(defn- process-deps
  [names opts cb]
  (run-sync! (fn [name cb]
                (cljs/require name opts cb))
    names
    :error
    cb))

(defn- process-macros-deps
  [cache cb]
  (process-deps (distinct (vals (:require-macros cache))) {:macros-ns true} cb))

(defn- process-libs-deps
  [cache cb]
  (process-deps (distinct (concat (vals (:requires cache)) (vals (:imports cache)))) {} cb))

(declare execute-source)

(defn- process-execute-path
  [file opts]
  (binding [theme (assoc theme :err-font (:verbose-font theme))]
    (load {:file file}
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
                            (js-eval source source-url))))))))
          (handle-error (js/Error. (str "Could not load file " file)) false))))))

(defn- dir*
  [nsname]
  (run! prn
    (distinct (sort (concat
                      (public-syms nsname)
                      (public-syms (add-macros-suffix nsname)))))))

(defn- apropos*
  [str-or-pattern]
  (let [matches? (if (instance? js/RegExp str-or-pattern)
                   #(re-find str-or-pattern (str %))
                   #(string/includes? (str %) (str str-or-pattern)))]
    (sort (mapcat (fn [ns]
                    (let [ns-name (drop-macros-suffix (str ns))]
                      (map #(symbol ns-name (str %))
                        (filter matches? (public-syms ns)))))
            (all-ns)))))

(defn- undo-reader-conditional-whitespace-docstring
  "Undoes the effect that wrapping a reader conditional around
  a defn has on a docstring."
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

(declare print-value)

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
  (println (str (when-let [ns (:ns m)] (str ns "/")) (:name m)))
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

(defn- doc*
  [sym]
  (if-let [special-sym ('{&       fn
                          catch   try
                          finally try} sym)]
    (doc* special-sym)
    (cond

      (special-doc-map sym)
      (print-doc (special-doc sym))

      (repl-special-doc-map sym)
      (print-doc (repl-special-doc sym))

      (get-namespace sym)
      (print-doc
        (select-keys (get-namespace sym) [:name :doc]))

      (get-var (get-aenv) sym)
      (print-doc
        (let [var (get-var (get-aenv) sym)
              var (assoc var :forms (-> var :meta :forms second)
                             :arglists (-> var :meta :arglists second))
              m (select-keys var
                  [:ns :name :doc :forms :arglists :macro :url])
              m (update m :doc undo-reader-conditional-whitespace-docstring)]
          (cond-> (update-in m [:name] name)
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

(defn- find-doc*
  [re-string-or-pattern]
  (let [re (re-pattern re-string-or-pattern)
        sym-docs (sort-by first
                   (mapcat (fn [ns]
                             (map (juxt first (comp :doc second))
                               (get-in @st [::ana/namespaces ns :defs])))
                     (all-ns)))]
    (doseq [[sym doc] sym-docs
            :when (and doc
                       (name sym)
                       (or (re-find re doc)
                           (re-find re (name sym))))]
      (doc* sym))))

(defn- source*
  [sym]
  (println (or (fetch-source (get-var (get-aenv) sym))
               "Source not found")))

(defn- pst*
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
    (or (and (first (js/PLANCK_LOAD (str src ".cljs"))) (str src ".cljs"))
        (str src ".cljc"))))

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
  {:*print-meta*   *print-meta*
   :*print-length* *print-length*
   :*print-level*  *print-level*
   :*unchecked-if* *unchecked-if*
   :*assert*       *assert*
   :*1             *1
   :*2             *2
   :*3             *3
   :*e             *e})

(defn- set-session-state
  "Sets the session state given a sesssion state map."
  [session-state]
  (set! *print-meta* (:*print-meta* session-state))
  (set! *print-length* (:*print-length* session-state))
  (set! *print-level* (:*print-level* session-state))
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
      (let [source (:source x)
            [file-namespace relpath] (extract-cache-metadata-mem source-text)
            cache (get-namespace file-namespace)]
        (write-cache relpath file-namespace source cache)))
    (cb {:value nil})))

(defn- print-value
  [value opts]
  (if *pprint-results*
    (if-let [[term-height term-width] (js/PLANCK_GET_TERM_SIZE)]
      ((if (::as-code? opts)
         planck.pprint.code/pprint
         planck.pprint.data/pprint)
        value {:width      ((fnil + 0) term-width (::term-width-adj opts))
               :theme      theme
               :spec?      (::spec? opts)
               :keyword-ns (::keyword-ns opts)})
      (prn value))
    (prn value)))

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
  (call-form? expression-form '#{require require-macros}))

(defn- def-form?
  "Determines if the expression is a def expression which returns a Var."
  [expression-form]
  (call-form? expression-form '#{def defn defn- defonce defmulti}))

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
            {:ns         initial-ns
             :verbose    (:verbose @app-env)
             :static-fns (:static-fns @app-env)}
            (if expression?
              (merge {:context       :expr
                      :def-emits-var true}
                (when (load-form? expression-form)
                  {:source-map true}))
              (merge {:source-map true}
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
  [[source-type source-value] {:keys [expression?] :as opts}]
  (binding [ana/*cljs-ns* @current-ns
            *ns* (create-ns @current-ns)
            cljs/*load-fn* load
            cljs/*eval-fn* caching-js-eval
            r/*data-readers* tags/*cljs-data-readers*]
    (if-not (= "text" source-type)
      (process-execute-path source-value (assoc opts :source-path source-value))
      (let [source-text source-value
            expression-form (and expression? (first (repl-read-string source-text)))]
        (if (repl-special? expression-form)
          (process-repl-special expression-form opts)
          (process-execute-source source-text expression-form opts))))))


;; The following atoms and fns set up a scheme to
;; emit function values into JavaScript as numeric
;; references that are looked up.

(defonce ^:private fn-index (atom 0))
(defonce ^:private fn-refs (atom {}))

(defn- clear-fns!
  "Clears saved functions."
  []
  (reset! fn-refs {}))

(defn- put-fn
  "Saves a function, returning a numeric representation."
  [f]
  (let [n (swap! fn-index inc)]
    (swap! fn-refs assoc n f)
    n))

(defn- get-fn
  "Gets a function, given its numeric representation."
  [n]
  (get @fn-refs n))

(defn- emit-fn [f]
  (print "planck.repl.get_fn(" (put-fn f) ")"))

(defmethod comp/emit-constant js/Function
  [f]
  (emit-fn f))

(defmethod comp/emit-constant cljs.core/Var
  [f]
  (emit-fn f))

(defn- ^:export execute
  [source expression? print-nil-expression? set-ns theme-id session-id]
  (clear-fns!)
  (reset-show-indicator!)
  (when set-ns
    (reset! current-ns (symbol set-ns)))
  (binding [theme (get-theme (keyword theme-id))]
    (execute-source source {:expression?           expression?
                            :print-nil-expression? print-nil-expression?
                            :include-stacktrace?   true
                            :session-id            session-id})))

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
  (eval `(~'var ~sym) ns))

(defn- resolve
  [sym]
  (ns-resolve (.-name *ns*) sym))

(defn- intern
  ([ns name]
   (when-let [the-ns (find-ns (cond-> ns (instance? Namespace ns) ns-name))]
     (eval `(def ~name) (ns-name the-ns))))
  ([ns name val]
   (when-let [the-ns (find-ns (cond-> ns (instance? Namespace ns) ns-name))]
     (eval `(def ~name ~val) (ns-name the-ns)))))

(defn- inject-planck-eval
  [target-ns]
  (intern target-ns 'eval eval))

(defn- ^:export wrap-color-err
  []
  (let [orig-print-err-fn js/PLANCK_PRINT_ERR_FN]
    (set! js/PLANCK_PRINT_ERR_FN
      (fn [msg]
        (orig-print-err-fn (:err-font theme))
        (orig-print-err-fn msg)
        (orig-print-err-fn (:reset-font theme))))))
