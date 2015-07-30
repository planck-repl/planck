(ns script.bootstrap.build
  (:require [cljs.build.api :as api]))

(println "Building")
(api/build (api/inputs "src")
  {:output-dir    "out"
   :output-to     "out/main.js"
   :optimizations :none
   :static-fns    true})
(println "Done building")
(System/exit 0)
