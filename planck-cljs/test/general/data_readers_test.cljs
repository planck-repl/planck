(ns general.data-readers-test
  (:require
   [cljs.test :refer-macros [deftest is]]
   [foo.data-readers]))

(deftest data-reader-test
  (is (= [3 2 1] #foo/bar [1 2 3])))
