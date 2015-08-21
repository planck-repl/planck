(ns planck.core-test
  (:require [cljs.test :refer-macros [deftest testing is]]
            [planck.core]))

(deftest exit-throws
  (testing "exit throws EXIT exception"
    (is (thrown-with-msg? js/Error #"PLANCK_EXIT" (planck.core/exit 112))))
  (testing "exit sets global exit code"
    (is (= 112 (js/PLANCK_GET_EXIT_VALUE)))))
