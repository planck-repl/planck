(ns general.fipp-test
  "A test namespace to ensure we are bundling all
  of the Fipp namespaces."
  (:require
   [clojure.test :refer [deftest is]]
   [fipp.clojure]
   [fipp.deque]
   [fipp.edn]
   [fipp.ednize]
   [fipp.engine]
   [fipp.visit]))

(deftest nonce-test
  (is (= 1 1)))

