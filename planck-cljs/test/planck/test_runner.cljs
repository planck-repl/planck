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

(defn run-all-spec-tests
  []
  (let [failed (->> (stest/enumerate-namespace
                      '[#_planck.core
                        planck.repl])
                 stest/check
                 (filter :failure))]
    (when-not (empty? failed)
      (fipp.edn/pprint (map #(select-keys % [:sym :failure]) failed))
      (exit 1))))

(defn run-all-regular-tests []
  (run-tests
    'planck.core-test
    'planck.io-test
    'planck.shell-test
    'planck.repl-test
    'planck.js-deps-test
    'planck.http-test
    'general.core-test
    'general.fipp-test))

(defn run-all-tests []
  (run-all-spec-tests)
  (run-all-regular-tests))
