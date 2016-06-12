(ns planck.test-runner
  (:require [cljs.test :refer-macros [run-tests]]
            [cljs.spec :as s]
            [planck.core-test]
            [planck.io-test]
            [planck.shell-test]
            [planck.repl-test]
            [planck.js-deps-test]
            [planck.http-test]
            [general.core-test]))

(defn run-all-tests []
  (s/instrument-all)
  (run-tests
    'planck.core-test
    'planck.io-test
    'planck.shell-test
    'planck.repl-test
    'planck.js-deps-test
    'planck.http-test
    'general.core-test))
