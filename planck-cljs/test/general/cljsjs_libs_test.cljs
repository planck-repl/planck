(ns general.cljsjs-libs-test
  (:require
   [cljs.test :refer-macros [deftest is]]
   [cljsjs.long]))

(deftest cljsjs-long-loaded
  (is (= "9223372036854775807" (str (js/Long. 0xFFFFFFFF 0x7FFFFFFF)))))
