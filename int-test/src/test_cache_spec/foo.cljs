(ns test-cache-spec.foo
  (:require 
   [test-cache-spec.bar :as bar]
   [clojure.spec.alpha :as s]))

(defn valid? [x]
  (s/valid? ::bar/int x))
