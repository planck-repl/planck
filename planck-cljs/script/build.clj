(ns script.bootstrap.build
  (:require [clojure.java.io :as io]
            [cljs.build.api :as api]
            [cljs.analyzer]
            [cognitect.transit :as transit])
  (:import [java.io ByteArrayOutputStream FileInputStream]))

(defn write-cache [cache out-path]
  (let [out (ByteArrayOutputStream. 1000000)
        writer (transit/writer out :json)]
    (transit/write writer cache)
    (spit (io/file out-path) (.toString out))))

(cljs.analyzer/with-warning-handlers
  [(fn [warning-type env extra]
     (when (warning-type cljs.analyzer/*cljs-warnings*)
       (when-let [s (cljs.analyzer/error-message warning-type extra)]
         (binding [*out* *err*]
           (println "WARNING:" (cljs.analyzer/message env s))
           (System/exit 1)))))]
  (api/build (api/inputs "src")
    {:output-dir         "out"
     :output-to          "out/main.js"
     :optimizations      :none
     :static-fns         true
     :optimize-constants false
     :dump-core          false
     :parallel-build     false
     :compiler-stats     false}))

(defn copy-source
  [filename]
  (spit (str "out/" filename)
    (slurp (io/resource filename))))

(copy-source "cljs/test.cljc")
(copy-source "cljs/spec/alpha.cljc")
(copy-source "cljs/spec/test/alpha.cljc")
(copy-source "cljs/spec/test/alpha.cljs")
(copy-source "cljs/spec/gen/alpha.cljc")
(copy-source "cljs/analyzer/api.cljc")
(copy-source "clojure/template.clj")

(let [res (io/resource "cljs/core.cljs.cache.aot.edn")
      cache (read-string (slurp res))]
  (doseq [key (keys cache)]
    (write-cache (key cache) (str "out/cljs/core.cljs.cache.aot." (munge key) ".json"))))

(let [res "out/cljs/core$macros.cljc.cache.json"
      cache (transit/read (transit/reader (FileInputStream. res) :json))]
  (doseq [key (keys cache)]
    (write-cache (key cache) (str "out/cljs/core$macros.cljc.cache." (munge key) ".json"))))

(System/exit 0)
