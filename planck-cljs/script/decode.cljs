(ns script.bootstrap.build
  (:require
   [cljs.source-map :as sm]
   [cognitect.transit :as transit]
   [planck.core :refer [slurp spit]]
   [planck.repl :refer [strip-source-map]]))

(defn cljs->transit-json
  [x]
  (let [wtr (transit/writer :json)]
    (transit/write wtr x)))

(let [file (first *command-line-args*)
      sm-json (slurp file)
      decoded (sm/decode (.parse js/JSON sm-json))
      stripped (#'strip-source-map decoded)
      transit-json (cljs->transit-json stripped)]
  (spit file transit-json))
