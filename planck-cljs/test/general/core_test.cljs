(ns general.core-test
  "General tests to ensure Planck is operating correctly.
  These tests are not testing any specific Planck namespace."
  (:require-macros [clojure.template :refer [do-template]])
  (:require [cljs.test :refer-macros [deftest is]]))

(deftest do-template-test
  (is (= '(do (+ 4 2) (+ 5 3))
        (macroexpand '(do-template [x y] (+ y x) 2 4 3 5)))))
