(ns planck.environ
  "Facilities for working with environment variables."
  (:require
   [clojure.string :as string]
   [goog.object :as gobj]))

(defn- keywordize [s]
  (-> (string/lower-case s)
      (string/replace "_" "-")
      (string/replace "." "-")
      (keyword)))

(defn- read-system-env []
  (let [env-obj (js/PLANCK_GETENV)]
    (into {} (for [k (js-keys env-obj)]
               [(keywordize k) (gobj/get env-obj k)]))))

(defonce ^{:doc "A map of environment variables."}
  env
  (read-system-env))
