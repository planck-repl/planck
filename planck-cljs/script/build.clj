(ns script.bootstrap.build
  (:require [clojure.java.io :as io]
            [cljs.build.api :as api]
            [cognitect.transit :as transit])
  (:import [java.io ByteArrayOutputStream]))

(defn extract-analysis-cache [res out-path]
  (let [out (ByteArrayOutputStream. 1000000)
        writer (transit/writer out :json)
        cache (read-string
                (slurp res))]
    (transit/write writer cache)
    (spit (io/file out-path) (.toString out))))



(println "Building")
(api/build (api/inputs "src")
  {:output-dir         "out"
   :output-to          "out/main.js"
   :optimizations      :none
   :static-fns         true
   :optimize-constants false
   :dump-core          false})

(extract-analysis-cache (io/resource "cljs/core.cljs.cache.aot.edn") "out/cljs/core.cljs.cache.aot.json")
#_(extract-analysis-cache "out/cljs/core$macros.cljc.cache.edn" "out/cljs/core$macros.cljc.cache.json")

(println "Done building")
(System/exit 0)
