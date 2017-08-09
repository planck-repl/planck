(ns planck.closure
  "Provides access to the Closure compiler"
  (:require
   [cljs.source-map :as sm]))

(defn compile
  "Uses Closure to compile JavaScript source. If :sm-data is supplied, a
  composed :source-map will calculated and be returned in the result."
  [{:keys [source sm-data optimizations]
    :or {optimizations :simple}}]
  (when-not (exists? js/compile)
    (js/AMBLY_IMPORT_SCRIPT "jscomp.js"))
  (let [result (js/compile #js {:jsCode                   #js [#js {:src source}]
                                :compilationLevel         (case optimizations
                                                            :simple "SIMPLE"
                                                            :whitespace "WHITESPACE_ONLY")
                                :languageIn               "ECMASCRIPT3"
                                :languageOut              "ECMASCRIPT3"
                                :processClosurePrimitives false
                                :createSourceMap          (some? sm-data)
                                :applyInputSourceMaps     false})]
    (when-not (empty? (.-errors result))
      (prn (.-errors result)))
    (if (.-compiledCode result)
      (merge {:source (.-compiledCode result)}
        (when (some? sm-data)
          {:source-map (->> result
                         .-sourceMap
                         (.parse js/JSON)
                         sm/decode-reverse
                         vals
                         first
                         (sm/merge-source-maps (:source-map sm-data))
                         sm/invert-reverse-map)}))
      {:source source})))
