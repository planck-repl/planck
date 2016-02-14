(ns planck.repl-test
  (:require-macros [planck.repl])
  (:require [cljs.test :refer-macros [deftest testing is]]
            [planck.repl :as repl]))

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

#_(deftest handle-error-non-exit-exception-non-exit-context
  (js/PLANCK_SET_EXIT_VALUE -1)
  (let [result (planck.repl/handle-error (js/Error. "Original Error") false false)]
    (testing "Non-exit exception outside of exit context returns original error"
      (is (= "Original Error" (.-message result))))
    (testing "Non-exit exception outside of exit context preserves existing exit code"
      (is (= -1 (js/PLANCK_GET_EXIT_VALUE)))))
  (js/PLANCK_SET_EXIT_VALUE 0))

#_(deftest handle-error-exit-exception-non-exit-context
  (js/PLANCK_SET_EXIT_VALUE 47)
  (let [result (planck.repl/handle-error (js/Error. "PLANCK_EXIT") false false)]
    (testing "Exit exception outside of exit context returns original error"
      (is (= "PLANCK_EXIT" (.-message result))))
    (testing "Exit exception outside of exit context preserves existing exit code"
      (is (= 47 (js/PLANCK_GET_EXIT_VALUE)))))
  (js/PLANCK_SET_EXIT_VALUE 0))

#_(deftest handle-error-non-exit-exception-in-exit-context
  (js/PLANCK_SET_EXIT_VALUE -1)
  (let [result (planck.repl/handle-error (js/Error. "Original Error") false true)]
    (testing "Non-exit exception in exit context returns nothing"
      (is (= nil result)))
    (testing "Non-exit exception in exit context sets a default exit code"
      (is (= 1 (js/PLANCK_GET_EXIT_VALUE)))))
  (js/PLANCK_SET_EXIT_VALUE 0))

#_(deftest handle-error-exit-exception-in-exit-context
  (js/PLANCK_SET_EXIT_VALUE 32)
  (let [result (planck.repl/handle-error (js/Error. "PLANCK_EXIT") false true)]
    (testing "Exit exception in exit context returns exit error"
      (is (= "PLANCK_EXIT" (.-message result))))
    (testing "Exit exception in exit context preserves existing exit code"
      (is (= 32 (js/PLANCK_GET_EXIT_VALUE)))))
  (js/PLANCK_SET_EXIT_VALUE 0))

#_(deftest read-eval-print-exception-in-exit-context
  (js/PLANCK_SET_EXIT_VALUE 0)
  (let [result (planck.repl/execute ["text" "(throw (js/Error. \"bye-bye\"))"] true false true nil true)]
    (testing "default exception return code as a side-effect"
      (is (= 1 (js/PLANCK_GET_EXIT_VALUE))))
    (testing "returns nothing"
      (is (= nil result))))
  (js/PLANCK_SET_EXIT_VALUE 0))

#_(deftest read-eval-print-exception-outside-exit-context
  (js/PLANCK_SET_EXIT_VALUE 0)
  (let [result (planck.repl/execute ["text" "(throw (js/Error. \"bye-bye\"))"] true false false nil true)]
    (testing "doesn't touch exit value"
      (is (= 0 (js/PLANCK_GET_EXIT_VALUE))))
    (testing "returns error"
      (is (not (= nil result)))))
  (js/PLANCK_SET_EXIT_VALUE 0))

(deftest test-apropos
  (is (= '(cljs.core/ffirst) (planck.repl/apropos "ffirst")))
  (is (= '(cljs.core/ffirst) (planck.repl/apropos ffirst)))
  (is (= '(cljs.core/ffirst cljs.core/nfirst) (planck.repl/apropos #"[a-z]+first"))))

(deftest test-dir-planck-repl
  (is (= "apropos\ndir\ndoc\nfind-doc\npst\nsource\n" (with-out-str (planck.repl/dir planck.repl)))))
