(ns planck.core-test
  (:require [cljs.test :refer-macros [deftest testing is]]
            [planck.core]))

(deftest exit-throws
  (testing "exit throws EXIT exception"
    (is (thrown-with-msg? js/Error #"PLANCK_EXIT" (planck.core/exit 112))))
  (testing "exit sets global exit code"
    (is (= 112 (js/PLANCK_GET_EXIT_VALUE)))))

#_(deftest setTimeout-can-be-used
  "This test is intentionally a bit naieve since async macros wouldn't work at time of writing"
  (testing "setTimeout actually gets called and does something"
    (let [now #(.getTime (js/Date.))
          t (now)
          test-state (atom :foo)]
      (js/setTimeout (fn []
                       (reset! test-state :bar))
        100)
      (while (< (now) (+ t 500)))
      (is (= :bar @test-state)))))
