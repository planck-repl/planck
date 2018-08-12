(ns general.closure-libs-test
  (:require
   [cljs.test :refer-macros [deftest is]]
   [my-lib.core]
   [other-lib.core]))

(deftest my-closure-lib-loaded
  (is (= 5 (my-lib.core/add 2 3))))

(deftest other-closure-lib-loaded
  (is (= 3 (other-lib.core/subtract 5 2))))
