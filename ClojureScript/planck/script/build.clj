(ns script.bootstrap.build
  (:require [clojure.java.io :as io]
            [cljs.closure :as closure]
            [cljs.env :as env]
            [cljs.analyzer :as ana]
            [cljs.compiler :as c]
            [clojure.edn :as edn])
  (:import [java.io FileOutputStream]))

(defn compile1 [copts file]
  (let [targ (io/resource file)
        _ (println "Compiling:" targ)
        core-js (closure/compile targ
                  (assoc copts
                    :output-file (closure/src-file->target-file targ)))
        deps    (closure/add-dependencies copts core-js)]
    deps))

(defn edn->js [e]
  (env/with-compiler-env (env/default-compiler-env)
    (let [env (assoc (ana/empty-env) :context :expr
                                 :ns {:name 'cljs.core})
        form (edn/read-string (str "(quote " e ")"))
        ast (ana/analyze env form)
        js (with-out-str (c/emit ast))]
        js)))

(defn build [dir file opts]
  ;; Used to generate core$macros
  (io/make-parents "resources/cljs/core.cljc")
  (spit "resources/cljs/core.cljc" (slurp (io/resource "cljs/core.cljc")))

  (let [output-dir (io/file dir)
        copts (assoc opts
                :output-dir output-dir
                :cache-analysis true
                :source-map true
                :static-fns true)]
    (env/with-compiler-env (env/default-compiler-env opts)
      (let [;; Compile main file
            deps (compile1 copts file)
            ;; Generate core$macros
            deps-macros (compile1 copts "cljs/core.cljc")]
        ;; output unoptimized code and the deps file
        ;; for all compiled namespaces
        (apply closure/output-unoptimized
          (assoc copts
            :output-to (.getPath (io/file output-dir "deps.js")))
          (concat deps deps-macros)))))

  (spit "out/cljs/core.cache.js" 
    (str 
      "goog.provide('cljs.cache');\ngoog.require('cljs.core');\n\n"
      "cljs.cache.core = " (edn->js (edn/read-string (slurp (io/resource "cljs/core.cljs.cache.aot.edn")))) ";\n\n"
      "cljs.cache.core$macros = " (edn->js (edn/read-string (slurp "out/cljs/core$macros.cljc.cache.edn"))))) ";\n\n"

  (spit "out/deps.js" 
    (str (slurp "out/deps.js")
          "goog.addDependency(\"../cljs/core.cache.js\", ['cljs.cache'], ['cljs.core']);\n")))

(println "Building")
(build "out" "planck/core.cljs" nil)
(println "Done building")
