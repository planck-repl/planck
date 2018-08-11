(ns general.closure-libs-test
  (:require
   [cljs.test :refer-macros [deftest is]]
   [my-lib.core]))

(deftest closure-lib-loaded
  (is (= 5 (my-lib.core/add 2 3))))
