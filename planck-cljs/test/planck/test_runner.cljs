(ns planck.test-runner
  (:require
   [cljs.spec.test :as stest]
   [clojure.test :refer [run-tests]]
   [fipp.edn]
   [general.core-test]
   [general.fipp-test]
   [planck.core :refer [exit]]
   [planck.core-test]
   [planck.http-test]
   [planck.io-test]
   [planck.js-deps-test]
   [planck.repl-test]
   [planck.shell-test]))

(defn run-all-tests []
  (run-tests
    'planck.core-test
    'planck.io-test
    'planck.shell-test
    'planck.repl-test
    'planck.js-deps-test
    'planck.http-test
    'general.core-test
    'general.fipp-test))
