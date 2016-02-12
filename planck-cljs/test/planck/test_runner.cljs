(ns planck.test-runner
  (:require [cljs.test :refer-macros [run-tests]]
            [planck.core-test]
            [planck.io-test]
            [planck.shell-test]
            [planck.repl-test]
            [planck.js-deps-test]
            [planck.http-test]
            [general.core-test]))

(defn run-all-tests []
  (run-tests
    'planck.core-test
    'planck.io-test
    'planck.shell-test
    'planck.repl-test
    'planck.js-deps-test
    'planck.http-test
    'general.core-test))
