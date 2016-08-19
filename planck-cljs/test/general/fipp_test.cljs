(ns general.fipp-test
  "A test namespace to ensure we are bundling all
  of the Fipp namespaces."
  (:require fipp.clojure
            fipp.deque
            fipp.edn
            fipp.ednize
            fipp.engine
            fipp.visit
            [clojure.test :refer [deftest is]]))

(deftest nonce-test
  (is (= 1 1)))

