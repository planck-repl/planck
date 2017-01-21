(ns planck.test-runner
  (:require [clojure.test :refer [run-tests]]
            [cljs.spec.test :as stest]
            [planck.core :refer [exit]]
            [planck.core-test]
            [planck.io-test]
            [planck.shell-test]
            [planck.repl-test]
            [planck.js-deps-test]
            [planck.http-test]
            [fipp.edn]
            [general.core-test]
            [general.fipp-test]))

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
