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

#_(extract-analysis-cache "out/clojure/set.cljs.cache.edn" "out/clojure/set.cljs.cache.json")
#_(extract-analysis-cache "out/clojure/string.cljs.cache.edn" "out/clojure/string.cljs.cache.json")
#_(extract-analysis-cache "out/clojure/walk.cljs.cache.edn" "out/clojure/walk.cljs.cache.json")

#_(extract-analysis-cache "out/cljs/reader.cljs.cache.edn" "out/cljs/reader.cljs.cache.json")
#_(extract-analysis-cache "out/cljs/tagged_literals.cljc.cache.edn" "out/cljs/tagged_literals.cljc.cache.json")
#_(extract-analysis-cache "out/cljs/pprint.cljs.cache.edn" "out/cljs/pprint.cljs.cache.json")
#_(extract-analysis-cache "out/cljs/test.cljs.cache.edn" "out/cljs/test.cljs.cache.json")

(extract-analysis-cache "out/planck/core.cljs.cache.edn" "out/planck/core.cljs.cache.json")
(extract-analysis-cache "out/planck/io.cljs.cache.edn" "out/planck/io.cljs.cache.json")
(extract-analysis-cache "out/planck/shell.cljs.cache.edn" "out/planck/shell.cljs.cache.json")

(println "Done building")
(System/exit 0)
