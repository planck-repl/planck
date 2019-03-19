(ns foo.core
  (:refer-clojure :exclude [map])
  (:require
   [clojure.string :as string]
   [clojure.set :as set :refer [union intersection]]))

;; A test namespace for testing interns, ns-resolve, ns-aliases, ns-refers

(def h 3)
