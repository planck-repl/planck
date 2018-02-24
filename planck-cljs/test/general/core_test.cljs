(ns general.core-test
  "General tests to ensure Planck is operating correctly.
  These tests are not testing any specific Planck namespace."
  (:require-macros
   [clojure.template :refer [do-template]])
  (:require
   [cljs.test :refer-macros [deftest is]]))

(deftest do-template-test
  (is (= '(do (+ 4 2) (+ 5 3))
        (macroexpand '(do-template [x y] (+ y x) 2 4 3 5)))))

(deftest system-timer-monkey-patch-test
  ; Ensure we haven't broken system-time
  (let [t0 (system-time)
        t1 (system-time)]
    (is (<= t0 t1))))

(deftest clojurescript-version-test
  (is (some? *clojurescript-version*)))
