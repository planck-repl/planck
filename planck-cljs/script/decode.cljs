(ns script.bootstrap.build
  (:require
   [cljs.source-map :as sm]
   [cognitect.transit :as transit]
   [planck.core :refer [slurp spit]]))

(defn cljs->transit-json
  [x]
  (let [wtr (transit/writer :json)]
    (transit/write wtr x)))

(let [file (first *command-line-args*)
      sm-json (slurp file)
      decoded (sm/decode (.parse js/JSON sm-json))
      decoded-transit-json (cljs->transit-json decoded)]
  (spit file decoded-transit-json))
