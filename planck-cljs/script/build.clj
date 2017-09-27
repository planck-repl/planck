(ns script.bootstrap.build
  (:require [clojure.java.io :as io]
            [cljs.build.api :as api]
            [cljs.analyzer]
            [cognitect.transit :as transit])
  (:import [java.io ByteArrayOutputStream FileInputStream]))

(def canary-build? (boolean (System/getenv "CANARY_BUILD")))

(def ci-build? (or canary-build?
                   (boolean (System/getenv "TRAVIS_OS_NAME"))))

(def checked-arrays (cond
                      ci-build? :error
                      (System/getenv "CLJS_CHECKED_ARRAYS") (keyword (System/getenv "CLJS_CHECKED_ARRAYS"))
                      :else false))

(def non-fatal-warnings #{:redef})

(cljs.analyzer/with-warning-handlers
  [(fn [warning-type env extra]
     (when (warning-type cljs.analyzer/*cljs-warnings*)
       (when-let [s (cljs.analyzer/error-message warning-type extra)]
         (binding [*out* *err*]
           (println "WARNING:" (cljs.analyzer/message env s)))
         (when-not (warning-type non-fatal-warnings)
           (System/exit 1)))))]
  (api/build (api/inputs "src")
    {:output-dir         "out"
     :output-to          "out/main.js"
     :optimizations      :none
     :static-fns         true
     :optimize-constants false
     :dump-core          false
     :checked-arrays     checked-arrays
     :parallel-build     false
     :foreign-libs       [{:file "jscomp.js"
                           :provides ["google-closure-compiler-js"]}]
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

(defn write-cache [cache out-path]
  (let [out (ByteArrayOutputStream. 1000000)
        writer (transit/writer out :json)]
    (transit/write writer cache)
    (spit (io/file out-path) (.toString out))))

(let [res (io/resource "cljs/core.cljs.cache.aot.edn")
      cache (read-string (slurp res))]
  (doseq [key (keys cache)]
    (write-cache (key cache) (str "out/cljs/core.cljs.cache.aot." (munge key) ".json"))))

(let [res "out/cljs/core$macros.cljc.cache.json"
      cache (transit/read (transit/reader (FileInputStream. res) :json))]
  (doseq [key (keys cache)]
    (write-cache (key cache) (str "out/cljs/core$macros.cljc.cache." (munge key) ".json"))))

(System/exit 0)
