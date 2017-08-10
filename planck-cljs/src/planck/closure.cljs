(ns planck.closure
  "Provides access to the Closure compiler"
  (:require
   [cljs.source-map :as sm]))

(defn- call-compiler
  [name source sm-data optimizations verbose]
  (when-not (exists? js/compile)
    (js/AMBLY_IMPORT_SCRIPT "jscomp.js"))
  (when verbose
    (println "Applying" optimizations "Closure optimizations to" name))
  (try
    (js/compile #js {:jsCode                   #js [#js {:src source}]
                     :compilationLevel         (case optimizations
                                                 :simple "SIMPLE"
                                                 :whitespace "WHITESPACE_ONLY")
                     :languageIn               "ECMASCRIPT3"
                     :languageOut              "ECMASCRIPT3"
                     :processClosurePrimitives false
                     :createSourceMap          (some? sm-data)
                     :applyInputSourceMaps     false})
    (catch :default e
      (throw (ex-info "Internal error running Closure compiler"
               {:name name, :optimizations optimizations} e)))))

(defn- check-compilation-results
  [name optimizations results]
  (when-not (empty? (.-warnings results))
    (println "Closure compilation warnings" (.-warnings results)))
  (when-not (empty? (.-errors results))
    (println "Closure compilation errors" (.-errors results))
    (throw (ex-info "Closure compilation errors" {:name          name
                                                  :optimizations optimizations
                                                  :errors        (.-errors results)}))))

(defn- extract-results
  [source sm-data results]
  (if (.-compiledCode results)
    (merge {:source (.-compiledCode results)}
      (when (some? sm-data)
        {:source-map (->> results
                       .-sourceMap
                       (.parse js/JSON)
                       sm/decode-reverse
                       vals
                       first
                       (sm/merge-source-maps (:source-map sm-data))
                       sm/invert-reverse-map)}))
    {:source source}))

(defn compile
  "Uses Closure to compile JavaScript source. If :sm-data is supplied, a
  composed :source-map will calculated and be returned in the result."
  [{:keys [name source sm-data optimizations verbose]
    :or   {optimizations :simple}}]
  (->> (call-compiler name source sm-data optimizations verbose)
    (check-compilation-results name optimizations)
    (extract-results source sm-data)))
