(ns planck.closure
  "Provides access to the Closure compiler")

(defn compile
  [source]
  (when-not (exists? js/compile)
    (js/AMBLY_IMPORT_SCRIPT "jscomp.js"))
  (let [result (js/compile #js {:jsCode #js [#js {:src source}]
                                :languageIn "ES3"
                                :languageOut "ES3"
                                :processClosurePrimitives false})]
    (when-not (empty? (.-errors result))
      (prn (.-errors result)))
    (or (.-compiledCode result)
        source)))
