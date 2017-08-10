(ns planck.test-runner
  (:require
   [clojure.spec.test.alpha :as st]
   [clojure.test :refer [run-tests]]
   [fipp.edn]
   [general.core-test]
   [general.fipp-test]
   [planck.closure-test]
   [planck.core :refer [exit]]
   [planck.core-test]
   [planck.http-test]
   [planck.io-test]
   [planck.js-deps-test]
   [planck.repl-test]
   [planck.shell-test]
   [planck.socket.alpha-test]))

#_(st/instrument)

(defn run-all-tests []
  (run-tests
    'planck.socket.alpha-test
    'planck.core-test
    'planck.io-test
    'planck.shell-test
    'planck.repl-test
    'planck.js-deps-test
    'planck.http-test
    'planck.closure-test
    'general.core-test
    'general.fipp-test))
