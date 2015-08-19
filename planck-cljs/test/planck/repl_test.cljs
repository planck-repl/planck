(ns planck.repl-test
  (:require [cljs.test :refer-macros [deftest testing is]]
            [planck.repl]))

(deftest get-highlight-coords
  (testing "Highlight coordinates"
    (is (= [0 0] (js->clj (planck.repl/get-highlight-coords 1 "[]" []))))
    (is (= [0 1] (js->clj (planck.repl/get-highlight-coords 2 "[[]]" []))))
    (is (= [0 0] (js->clj (planck.repl/get-highlight-coords 3 "[()]" []))))
    (is (= [0 1] (js->clj (planck.repl/get-highlight-coords 2 " []" []))))
    (is (= [1 0] (js->clj (planck.repl/get-highlight-coords 1 "]" ["["]))))
    (is (= [2 0] (js->clj (planck.repl/get-highlight-coords 1 "]" ["[" ""]))))
    (is (= [1 0] (js->clj (planck.repl/get-highlight-coords 1 "]" ["[" "["]))))
    (is (= [2 0] (js->clj (planck.repl/get-highlight-coords 1 "]" ["[" "[]"]))))
    (is (= [0 1] (js->clj (planck.repl/get-highlight-coords 2 "#{}" []))))
    (is (= [0 0] (js->clj (planck.repl/get-highlight-coords 4 "[\"[\"]" []))))))
