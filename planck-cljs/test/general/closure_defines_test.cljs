(ns general.closure-defines-test
  (:require
   [cljs.test :refer-macros [deftest is]]
   [foo.closure-defines]))

(deftest closure-defines-test
  (is (= "symbol" foo.closure-defines/bar))
  (is (= "string" foo.closure-defines/baz)))
