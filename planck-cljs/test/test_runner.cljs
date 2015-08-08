(ns test-runner
  (:require [cljs.test :refer-macros [run-tests]]
            [planck.core-test]))

(run-tests
  'planck.core-test)
