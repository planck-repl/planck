(ns script.bootstrap.build
  (:require [clojure.java.io :as io]
            [cljs.build.api :as api]
            [cognitect.transit :as transit])
  (:import [java.io ByteArrayOutputStream]))

(defn write-cache [cache out-path]
  (let [out (ByteArrayOutputStream. 1000000)
        writer (transit/writer out :json)]
    (transit/write writer cache)
    (spit (io/file out-path) (.toString out))))

(defn extract-analysis-cache [res out-path]
  (let [cache (read-string (slurp res))]
    (write-cache cache out-path)))

(println "Building")
(api/build (api/inputs "src" "test") ;; For now, pre-compile tests
  {:output-dir         "out"
   :output-to          "out/main.js"
   :optimizations      :none
   :static-fns         true
   :optimize-constants false
   :dump-core          false
   :parallel-build     true})

(let [res (io/resource "cljs/core.cljs.cache.aot.edn")
      cache (read-string (slurp res))]
  (doseq [key (keys cache)]
    (write-cache (key cache) (str "out/cljs/core.cljs.cache.aot." (munge key) ".json"))))

(let [res "out/cljs/core$macros.cljc.cache.edn"
      cache (read-string (slurp res))]
  (doseq [key (keys cache)]
    (write-cache (key cache) (str "out/cljs/core$macros.cljc.cache." (munge key) ".json"))))

(extract-analysis-cache "out/clojure/set.cljs.cache.edn" "out/clojure/set.cljs.cache.json")
(extract-analysis-cache "out/clojure/string.cljs.cache.edn" "out/clojure/string.cljs.cache.json")
(extract-analysis-cache "out/clojure/data.cljs.cache.edn" "out/clojure/data.cljs.cache.json")
(extract-analysis-cache "out/clojure/walk.cljs.cache.edn" "out/clojure/walk.cljs.cache.json")
(extract-analysis-cache "out/clojure/zip.cljs.cache.edn" "out/clojure/zip.cljs.cache.json")

(extract-analysis-cache "out/cljs/analyzer.cljc.cache.edn" "out/cljs/analyzer.cljc.cache.json")
(extract-analysis-cache "out/cljs/compiler.cljc.cache.edn" "out/cljs/compiler.cljc.cache.json")
(extract-analysis-cache "out/cljs/env.cljc.cache.edn" "out/cljs/env.cljc.cache.json")
(extract-analysis-cache "out/cljs/js.cljs.cache.edn" "out/cljs/js.cljs.cache.json")
(extract-analysis-cache "out/cljs/pprint.cljs.cache.edn" "out/cljs/pprint.cljs.cache.json")
(extract-analysis-cache "out/cljs/reader.cljs.cache.edn" "out/cljs/reader.cljs.cache.json")
(extract-analysis-cache "out/cljs/repl.cljs.cache.edn" "out/cljs/repl.cljs.cache.json")
(extract-analysis-cache "out/cljs/source_map.cljs.cache.edn" "out/cljs/source_map.cljs.cache.json")
(extract-analysis-cache "out/cljs/source_map/base64.cljs.cache.edn" "out/cljs/source_map/base64.cljs.cache.json")
(extract-analysis-cache "out/cljs/source_map/base64_vlq.cljs.cache.edn" "out/cljs/source_map/base64_vlq.cljs.cache.json")
(extract-analysis-cache "out/cljs/stacktrace.cljc.cache.edn" "out/cljs/stacktrace.cljc.cache.json")
(extract-analysis-cache "out/cljs/test.cljs.cache.edn" "out/cljs/test.cljs.cache.json")
(extract-analysis-cache "out/cljs/test.cljs.cache.edn" "out/cljs/test.cljs.cache.json")
(extract-analysis-cache "out/cljs/tagged_literals.cljc.cache.edn" "out/cljs/tagged_literals.cljc.cache.json")
(extract-analysis-cache "out/cljs/tools/reader/reader_types.cljs.cache.edn" "out/cljs/tools/reader/reader_types.cljs.cache.json")
(extract-analysis-cache "out/cljs/tools/reader/impl/commons.cljs.cache.edn" "out/cljs/tools/reader/impl/commons.cljs.cache.json")
(extract-analysis-cache "out/cljs/tools/reader/impl/utils.cljs.cache.edn" "out/cljs/tools/reader/impl/utils.cljs.cache.json")

(extract-analysis-cache "out/cognitect/transit.cljs.cache.edn" "out/cognitect/transit.cljs.cache.json")

(extract-analysis-cache "out/planck/repl.cljs.cache.edn" "out/planck/repl.cljs.cache.json")
(extract-analysis-cache "out/planck/repl_resources.cljs.cache.edn" "out/planck/repl_resources.cljs.cache.json")
(extract-analysis-cache "out/planck/themes.cljs.cache.edn" "out/planck/themes.cljs.cache.json")
(extract-analysis-cache "out/planck/core.cljs.cache.edn" "out/planck/core.cljs.cache.json")
(extract-analysis-cache "out/planck/io.cljs.cache.edn" "out/planck/io.cljs.cache.json")
(extract-analysis-cache "out/planck/shell.cljs.cache.edn" "out/planck/shell.cljs.cache.json")
(extract-analysis-cache "out/planck/from/io/aviso/ansi.cljs.cache.edn" "out/planck/from/io/aviso/ansi.cljs.cache.json")

(extract-analysis-cache "out/tailrecursion/cljson.cljs.cache.edn" "out/tailrecursion/cljson.cljs.cache.json")


;; For now, use pre-compiled tests
(extract-analysis-cache "out/planck/test_runner.cljs.cache.edn" "out/planck/test_runner.cljs.cache.json")
(extract-analysis-cache "out/planck/core_test.cljs.cache.edn" "out/planck/core_test.cljs.cache.json")
(extract-analysis-cache "out/planck/io_test.cljs.cache.edn" "out/planck/io_test.cljs.cache.json")
(extract-analysis-cache "out/planck/shell_test.cljs.cache.edn" "out/planck/shell_test.cljs.cache.json")
(extract-analysis-cache "out/planck/repl_test.cljs.cache.edn" "out/planck/repl_test.cljs.cache.json")

(println "Done building")
(System/exit 0)
