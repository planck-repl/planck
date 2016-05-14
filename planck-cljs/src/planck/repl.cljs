(ns planck.repl
  (:require-macros [cljs.env.macros :refer [with-compiler-env]])
  (:require [clojure.string :as s]
            [goog.string :as gstring]
            [cljs.analyzer :as ana]
            [cljs.compiler :as comp]
            [cljs.tools.reader :as r]
            [cljs.tools.reader.reader-types :as rt]
            [cljs.tagged-literals :as tags]
            [cljs.source-map :as sm]
            [cljs.env :as env]
            [cljs.js :as cljs]
            [cljs.repl :as repl]
            [cljs.stacktrace :as st]
            [cognitect.transit :as transit]
            [tailrecursion.cljson :refer [cljson->clj]]
            [planck.repl-resources :refer [special-doc-map repl-special-doc-map]]
            [planck.themes :refer [get-theme]]
            [lazy-map.core :refer-macros [lazy-map]]
            [cljsjs.parinfer]
            [planck.js-deps :as js-deps]
            [clojure.string :as string]
            [planck.pprint]))

(def ^{:dynamic true
       :doc     "*pprint-results* controls whether Planck REPL results are
  pretty printed. If it is bound to logical false, results
  are printed in a plain fashion. Otherwise, results are
  pretty printed."}
  *pprint-results* true)

(def ^:private expression-name "Expression")

(defn- calc-x-line [text pos line]
  (let [x (s/index-of text "\n")]
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
  (if (s/ends-with? ns-name "$macros")
    (apply str (drop-last 7 ns-name))
    ns-name))

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
  (ns-syms ns (comp not :private second)))

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
  (let [keys        [:use-macros :excludes :name :imports :requires :uses :defs :require-macros :cljs.analyzer/constants :doc]
        load-single (fn [key]
                      (transit-json->cljs (first (js/PLANCK_LOAD (str file-prefix (munge key) ".json")))))
        load-all    (fn []
                      (zipmap keys (map load-single keys)))
        load        (fn [key]
                      (let [cache (load-all)]
                        (cljs/load-analysis-cache! st ns-sym cache)
                        (key cache)))]
    (cljs/load-analysis-cache! st ns-sym
      (if eager
        (load-all)
        (lazy-map
          {:use-macros              (load :use-macros)
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

(defn- ^:export init
  [repl verbose cache-path static-fns]
  (load-core-analysis-caches repl)
  (let [opts (or (read-opts-from-file "opts.clj")
                 {})]
    (reset! planck.repl/app-env (merge {:verbose    verbose
                                        :cache-path cache-path
                                        :opts       opts}
                                  (when static-fns
                                    {:static-fns true})))
    (js-deps/index-foreign-libs opts)
    (js-deps/index-upstream-foreign-libs)))

(defn- read-chars
  [reader]
  (lazy-seq
    (when-let [ch (rt/read-char reader)]
      (cons ch (read-chars reader)))))

(defn- repl-read-string
  "Returns a vector of the first read form, and any balance text."
  [source]
  (binding [ana/*cljs-ns* @current-ns
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

(defn- canonicalize-specs
  [specs]
  (letfn [(canonicalize [quoted-spec-or-kw]
            (if (keyword? quoted-spec-or-kw)
              quoted-spec-or-kw
              (as-> (second quoted-spec-or-kw) spec
                (if (vector? spec) spec [spec]))))]
    (map canonicalize specs)))

(defn- purge-analysis-cache!
  [state ns]
  (swap! state (fn [m]
                 (assoc m ::ana/namespaces (dissoc (::ana/namespaces m) ns)))))

(defn- purge!
  [names]
  (doseq [name names]
    (purge-analysis-cache! st name))
  (apply swap! cljs.js/*loaded* disj names))

(defn- process-reloads!
  [specs]
  (if-let [k (some #{:reload :reload-all} specs)]
    (let [specs (->> specs (remove #{k}))]
      (if (= k :reload-all)
        (purge! @cljs.js/*loaded*)
        (purge! (map first specs)))
      specs)
    specs))

(defn- self-require?
  [specs]
  (some
    (fn [quoted-spec-or-kw]
      (and (not (keyword? quoted-spec-or-kw))
           (let [spec (second quoted-spec-or-kw)
                 ns   (if (sequential? spec)
                        (first spec)
                        spec)]
             (= ns @current-ns))))
    specs))

(defn- make-ns-form
  [kind specs target-ns]
  (if (= kind :import)
    (with-meta `(~'ns ~target-ns
                  (~kind
                    ~@(map (fn [quoted-spec-or-kw]
                             (if (keyword? quoted-spec-or-kw)
                               quoted-spec-or-kw
                               (second quoted-spec-or-kw)))
                        specs)))
      {:merge true :line 1 :column 1})
    (with-meta `(~'ns ~target-ns
                  (~kind
                    ~@(-> specs canonicalize-specs process-reloads!)))
      {:merge true :line 1 :column 1})))

(defn- log-ns-form
  [kind ns-form]
  (when (:verbose @app-env)
    (println-verbose "Implementing"
      (name kind)
      "via ns:\n  "
      (pr-str ns-form))))

(defn- process-require
  [kind cb specs]
  (let [current-st @st]
    (try
      (let [is-self-require? (and (= :kind :require) (self-require? specs))
            [target-ns restore-ns]
            (if-not is-self-require?
              [@current-ns nil]
              ['cljs.user @current-ns])]
        (cljs/eval
          st
          (let [ns-form (make-ns-form kind specs target-ns)]
            (log-ns-form kind ns-form)
            ns-form)
          (make-base-eval-opts)
          (fn [{e :error}]
            (when is-self-require?
              (reset! current-ns restore-ns))
            (when e
              (handle-error e false false)
              (reset! st current-st))
            (cb))))
      (catch :default e
        (handle-error e true false)
        (reset! st current-st)))))

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
    (filter (if allow-private?
              identity
              #(not (:private (:meta (val %)))))
      (apply merge
        ((juxt :defs :macros)
          (get-namespace ns-sym))))))

(defn- is-completion?
  [buffer-match-suffix candidate]
  (re-find (js/RegExp. (str "^" buffer-match-suffix)) candidate))

(def ^:private keyword-completions
  [:require :require-macros :import
   :refer :refer-macros :include-macros
   :keys :strs :syms
   :as :or
   :pre :post
   :let :when :while
   :clj :cljs
   :default
   :else
   :gen-class
   :keywordize-keys])

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
         (completion-candidates-for-ns (expand-typed-ns (symbol typed-ns)) false)
         (concat
           (map str keyword-completions)
           (map #(drop-macros-suffix (str %)) (all-ns))
           (map #(str % "/") (keys (current-alias-map)))
           (completion-candidates-for-ns 'cljs.core false)
           (completion-candidates-for-ns 'cljs.core$macros false)
           (completion-candidates-for-ns @current-ns true)
           (when top-form?
             (concat
               (map str (keys special-doc-map))
               (map str (keys repl-special-doc-map))))))))

(defn- ^:export get-completions
  [buffer]
  (let [top-form?            (re-find #"^\s*\(\s*[^()\s]*$" buffer)
        typed-ns             (second (re-find #"\(+(\b[a-zA-Z-.]+)/[a-zA-Z-]+$" buffer))]
    (let [buffer-match-suffix (re-find #":?[a-zA-Z-\.]*$" buffer)
          buffer-prefix       (subs buffer 0 (- (count buffer) (count buffer-match-suffix)))]
      (clj->js (if (= "" buffer-match-suffix)
                 []
                 (map #(str buffer-prefix %)
                   (sort
                     (filter (partial is-completion? buffer-match-suffix)
                       (completion-candidates top-form? typed-ns)))))))))

(defn- is-completely-readable?
  [source]
  (let [rdr (rt/indexing-push-back-reader source 1 "noname")]
    (binding [ana/*cljs-ns* @current-ns
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
  "Gets the highlight coordinates [line pos] for the previous matching
  brace. This is done by progressivly expanding source considered
  until a readable form is encountered with a matching brace on the
  other end. The coordinate system is such that line 0 is the current
  buffer line, line 1 is the previous line, and so on, and pos is the
  position in that line."
  [pos buffer previous-lines]
  (let [previous-lines  (js->clj previous-lines)
        previous-source (s/join "\n" previous-lines)
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
  (s/ends-with? (str (:name cache)) "$macros"))

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
  (let [candidate (s/replace file #".clj[sc]?$" suffix)]
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
  (subs source (inc (s/index-of source "\n"))))

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

(defn- load-and-callback!
  [name path macros lang cache-prefix cb]
  (let [[raw-load [source modified]] [js/PLANCK_LOAD (js/PLANCK_LOAD path)]
        [raw-load [source modified]] (if source
                                       [raw-load [source modified]]
                                       [js/PLANCK_READ_FILE (js/PLANCK_READ_FILE path)])]
    (when source
      (when name
        (swap! name-path assoc name path))
      (cb (merge
            {:lang   lang
             :source source}
            (when-not (= :js lang)
              (cached-callback-data name path macros cache-prefix source modified raw-load))))
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
      (and (= name 'cljs.env.macros) macros)
      (and (= name 'cljs.analyzer.macros) macros)
      (and (= name 'cljs.compiler.macros) macros)
      (and (= name 'cljs.repl) macros)
      (and (= name 'cljs.js) macros)
      (and (= name 'cljs.pprint) macros)
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
    (swap! foreign-files-loaded conj result)
    result))

(defn- file-content
  "Loads the content for a given file."
  [file]
  (first (or (js/PLANCK_READ_FILE file)
             (js/PLANCK_LOAD file))))

(defn- do-load-foreign
  [name cb]
  (let [files-to-load (js-deps/files-to-load name)
        _             (when (:verbose @app-env)
                        (println "Loading foreign libs files:" files-to-load))
        sources       (map file-content (not-yet-loaded files-to-load))]
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
  [e include-stacktrace? in-exit-context?]
  (let [cause                     (or (.-cause e) e)
        is-planck-exit-exception? (= "PLANCK_EXIT" (.-message cause))]
    (when-not is-planck-exit-exception?
      (print-error e include-stacktrace?))
    (if (and in-exit-context? (not is-planck-exit-exception?))
      (js/PLANCK_SET_EXIT_VALUE 1)
      (set! *e (skip-cljsjs-eval-error e)))))

(defn- ^:export run-main
  [main-ns & args]
  (let [main-args (js->clj args)]
    (binding [cljs/*load-fn* load
              cljs/*eval-fn* caching-js-eval]
      (process-require
        :require
        (fn [_]
          (cljs/eval-str st
            (str "(var -main)")
            nil
            (merge (make-base-eval-opts)
              {:ns         (symbol main-ns)
               :source-map true})
            (fn [{:keys [ns value error] :as ret}]
              (try
                (apply value args)
                (catch :default e
                  (handle-error e true true))))))
        `[(quote ~(symbol main-ns))]))
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
             (= "Could not eval Expression" (ex-message error)))
      (ex-cause error))
    ex-cause))

(defn- is-reader-or-analysis?
  "Indicates if an exception is a reader or analysis exception."
  [e]
  (and (instance? ExceptionInfo e)
       (some #{[:type :reader-exception] [:tag :cljs/analysis-error]} (ex-data e))))

(defn- form-demunge-map
  "Forms a map from munged function symbols (as they appear in stacktraces)
  to their unmunged forms."
  [ns]
  {:pre [(symbol? ns)]}
  (let [ns-str        (str ns)
        munged-ns-str (s/replace ns-str #"\." "$")]
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

(defn- demunge-sym
  [munged-sym]
  (let [demunge-maps (cons @core-demunge-map (non-core-demunge-maps))]
    (str (or (lookup-sym demunge-maps munged-sym)
             (demunge-protocol-fn demunge-maps munged-sym)
             (demunge-local demunge-maps munged-sym)
           munged-sym))))

(defn-  mapped-stacktrace-str
  ([stacktrace sms]
   (mapped-stacktrace-str stacktrace sms nil))
  ([stacktrace sms opts]
   (with-out-str
     (doseq [{:keys [function file line column]}
             (st/mapped-stacktrace stacktrace sms opts)]
       (println "\t"
         (str (when function (str (demunge-sym function) " "))
           "(" file (when line (str ":" line))
           (when column (str ":" column)) ")"))))))

(defn- print-error
  ([error]
   (print-error error true))
  ([error include-stacktrace?]
   (print-error error include-stacktrace? nil))
  ([error include-stacktrace? printed-message]
   (let [error               (skip-cljsjs-eval-error error)
         roa?                (is-reader-or-analysis? error)
         include-stacktrace? (or (= include-stacktrace? :pst)
                               (and include-stacktrace?
                                 (not roa?)))
         include-stacktrace? (if *planck-integration-tests*
                               false
                               include-stacktrace?)
         message             (if (instance? ExceptionInfo error)
                               (ex-message error)
                               (.-message error))]
     (when (or (not ((fnil s/starts-with? "") printed-message message))
             include-stacktrace?)
       (println (((if roa? :rdr-ann-err-fn :ex-msg-fn) theme) message)))
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
  (let [macros-ns-str (str macros-ns)
        base-ns-str   (subs macros-ns-str 0 (- (count macros-ns-str) 7))
        base-ns       (symbol base-ns-str)]
    (if-let [macro-var (with-compiler-env st
                         (resolve-var env (symbol macros-ns-str (name sym))))]
      (update (assoc macro-var :ns base-ns)
        :name #(symbol base-ns-str (name %))))))

(defn- all-macros-ns
  []
  (->> (all-ns)
    (filter #(s/ends-with? (str %) "$macros"))))

(defn- get-var
  [env sym]
  (let [var (or (with-compiler-env st (resolve-var env sym))
                (some #(get-macro-var env sym %) (all-macros-ns)))]
    (when var
      (if (= (namespace (:name var)) (str (:ns var)))
        (update var :name #(symbol (name %)))
        var))))

(defn- get-file-source
  [filepath]
  (if (symbol? filepath)
    (let [without-extension (s/replace
                              (s/replace (name filepath) #"\." "/")
                              #"-" "_")]
      (or
        (first (js/PLANCK_LOAD (str without-extension ".clj")))
        (first (js/PLANCK_LOAD (str without-extension ".cljc")))
        (first (js/PLANCK_LOAD (str without-extension ".cljs")))))
    (let [file-source (first (js/PLANCK_LOAD filepath))]
      (or file-source
          (first (js/PLANCK_LOAD (s/replace filepath #"^out/" "")))
        (first (js/PLANCK_LOAD (s/replace filepath #"^src/" "")))
        (first (js/PLANCK_LOAD (s/replace filepath #"^/.*/planck-cljs/src/" "")))))))

(defn- fetch-source
  [var]
  (when-let [filepath (or (:file var) (:file (:meta var)))]
    (when-let [file-source (get-file-source filepath)]
      (let [rdr (rt/source-logging-push-back-reader file-source)]
        (dotimes [_ (dec (:line var))] (rt/read-line rdr))
        (-> (r/read {:read-cond :allow :features #{:cljs}} rdr)
          meta :source)))))

(defn- run-async!
  "Like cljs.core/run!, but for an async procedure, and with the
  ability to break prior to processing the entire collection.

  Chains successive calls to the supplied procedure for items in
  the collection. The procedure should accept an item from the
  collection and a callback of one argument. If the break? predicate,
  when applied to the procedure callback value, yields a truthy
  result, terminates early calling the supplied cb with the callback
  value. Otherwise, when complete, calls cb with nil."
  [proc coll break? cb]
  (if (seq coll)
    (proc (first coll)
      (fn [res]
        (if (break? res)
          (cb res)
          (run-async! proc (rest coll) break? cb))))
    (cb nil)))

(defn- process-deps
  [names opts cb]
  (run-async! (fn [name cb]
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
  [file {:keys [in-exit-context?] :as opts}]
  (binding [theme (assoc theme :err-font (:verbose-font theme))]
    (load {:file file}
      (fn [{:keys [lang source source-url cache]}]
        (if source
          (case lang
            :clj (execute-source ["text" source] opts)
            :js (process-macros-deps cache
                  (fn [res]
                    (if-let [error (:error res)]
                      (handle-error (js/Error. error) false in-exit-context?)
                      (process-libs-deps cache
                        (fn [res]
                          (if-let [error (:error res)]
                            (handle-error (js/Error. error) false in-exit-context?)
                            (js-eval source source-url))))))))
          (handle-error (js/Error. (str "Could not load file " file)) false in-exit-context?))))))

(defn- dir*
  [nsname]
  (run! prn
    (distinct (sort (concat
                      (public-syms nsname)
                      (public-syms (symbol (str (name nsname) "$macros"))))))))

(defn- apropos*
  [str-or-pattern]
  (let [matches? (if (instance? js/RegExp str-or-pattern)
                   #(re-find str-or-pattern (str %))
                   #(s/includes? (str %) (str str-or-pattern)))]
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
      (s/replace-all s #"\n      ?" "\n  ")
      s)))

(defn- doc*
  [sym]
  (if-let [special-sym ('{&       fn
                          catch   try
                          finally try} sym)]
    (doc* special-sym)
    (cond

      (special-doc-map sym)
      (repl/print-doc (special-doc sym))

      (repl-special-doc-map sym)
      (repl/print-doc (repl-special-doc sym))

      (get-namespace sym)
      (repl/print-doc
        (select-keys (get-namespace sym) [:name :doc]))

      (get-var (get-aenv) sym)
      (repl/print-doc
        (let [var (get-var (get-aenv) sym)
              var (assoc var :forms (-> var :meta :forms second)
                             :arglists (-> var :meta :arglists second))
              m   (select-keys var
                    [:ns :name :doc :forms :arglists :macro :url])
              m   (update m :doc undo-reader-conditional-whitespace-docstring)]
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
  (let [re       (re-pattern re-string-or-pattern)
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
  [argument {:keys [in-exit-context?] :as opts}]
  (let [filename argument]
    (try
      (execute-source ["path" filename] opts)
      (catch :default e
        (handle-error e false in-exit-context?)))))

(defn- process-repl-special
  [expression-form {:keys [print-nil-expression? in-exit-context?] :as opts}]
  (let [argument (second expression-form)]
    (case (first expression-form)
      in-ns (process-in-ns argument)
      require (process-require :require identity (rest expression-form))
      require-macros (process-require :require-macros identity (rest expression-form))
      import (process-require :import identity (rest expression-form))
      load-file (process-load-file argument (assoc opts :expression? false)))
    (when print-nil-expression?
      (println (str (:results-font theme) "nil" (:reset-font theme))))))

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

(defn- print-result
  [value]
  (if *pprint-results*
    (if-let [[term-height term-width] (js/PLANCK_GET_TERM_SIZE)]
      (planck.pprint/pprint value {:width term-width 
                                   :theme theme})
      (prn value))
    (prn value)))

(defn- process-execute-source
  [source-text expression-form {:keys [expression? print-nil-expression? in-exit-context? include-stacktrace? source-path] :as opts}]
  (try
    (let [initial-ns @current-ns]
      ;; For expressions, do an extra no-op eval-str for :verbose printing side effects w/o :def-emits-var
      (when (and expression?
                 (:verbose @app-env))
        (binding [theme (assoc theme :err-font (:verbose-font theme))]
          (cljs/eval-str
            (atom @st)
            source-text
            expression-name
            (merge
              {:ns            initial-ns
               :source-map    false
               :verbose       true
               :static-fns    (:static-fns @app-env)
               :context       :expr
               :def-emits-var false
               :eval          identity})
            identity)))
      ;; Now eval-str for true side effects
      (cljs/eval-str
        st
        source-text
        (if expression?
          expression-name
          (or source-path "File"))
        (merge
          {:ns         initial-ns
           :verbose    (and (not expression?)
                            (:verbose @app-env))
           :static-fns (:static-fns @app-env)}
          (if-not expression? {:source-map true})
          (if expression?
            {:context       :expr
             :def-emits-var true}
            (when (:cache-path @app-env)
              {:cache-source (cache-source-fn source-text)})))
        (fn [{:keys [ns value error] :as ret}]
          (if expression?
            (when-not error
              (when (or print-nil-expression?
                      (not (nil? value)))
                (print-result value))
              (process-1-2-3 expression-form value)
              (reset! current-ns ns)
              nil))
          (when error
            (handle-error error include-stacktrace? in-exit-context?)))))
    (catch :default e
      (handle-error e include-stacktrace? in-exit-context?))))

(defn- execute-source
  [[source-type source-value] {:keys [expression?] :as opts}]
  (binding [ana/*cljs-ns* @current-ns
            *ns* (create-ns @current-ns)
            cljs/*load-fn* load
            cljs/*eval-fn* caching-js-eval
            r/*data-readers* tags/*cljs-data-readers*]
    (if-not (= "text" source-type)
      (process-execute-path source-value (assoc opts :source-path source-value))
      (let [source-text     source-value
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
  [source expression? print-nil-expression? in-exit-context? set-ns theme-id]
  (clear-fns!)
  (when set-ns
    (reset! current-ns (symbol set-ns)))
  (binding [theme (get-theme (keyword theme-id))]
    (execute-source source {:expression?           expression?
                            :print-nil-expression? print-nil-expression?
                            :in-exit-context?      in-exit-context?
                            :include-stacktrace?   true})))

(defn- eval
  ([form]
   (eval form @current-ns))
  ([form ns]
   (let [result (atom nil)]
     (cljs/eval st form
       {:ns            ns
        :context       :expr
        :def-emits-var true}
       (fn [{:keys [value error]}]
         (if error
           (handle-error error true false)
           (reset! result value))))
     @result)))

(defn- ns-resolve
  [ns sym]
  (eval `(~'var ~sym) ns))

(defn- resolve
  [sym]
  (ns-resolve @current-ns sym))

(defn- ^:export wrap-color-err
  []
  (let [orig-print-err-fn js/PLANCK_PRINT_ERR_FN]
    (set! js/PLANCK_PRINT_ERR_FN
      (fn [msg]
        (orig-print-err-fn (:err-font theme))
        (orig-print-err-fn msg)
        (orig-print-err-fn (:reset-font theme))))))
