(ns planck.repl-test
  (:require-macros [planck.repl])
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop :include-macros true]
            [clojure.test.check.clojure-test :refer-macros [defspec]]
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

(deftest test-apropos
  (is (= '(cljs.core/ffirst) (planck.repl/apropos "ffirst")))
  (is (= '(cljs.core/ffirst) (planck.repl/apropos ffirst)))
  (is (= '(cljs.core/ffirst cljs.core/nfirst) (planck.repl/apropos #"[a-z]+first"))))

(deftest test-dir-planck-repl
  (is (= "*pprint-results*\napropos\ndir\ndoc\nfind-doc\npst\nsource\n" (with-out-str (planck.repl/dir planck.repl)))))

(deftest get-error-indicator-test
  (is (= "             ⬆"
        (repl/get-error-column-indicator
          (ex-info repl/could-not-eval-expr {}
            (ex-info "" {:tag    :cljs/analysis-error
                         :column 3}))
          "foo.core"))))

(defspec first-element-is-min-after-sorting                 ;; the name of the test
  100                                                       ;; the number of iterations for test.check to test
  (prop/for-all [v (gen/not-empty (gen/vector gen/int))]
    (= (apply min v)
      (first (sort v)))))

(deftest undo-reader-conditional-whitespace-docstring-test
  (is (= "a\n  b" (planck.repl/undo-reader-conditional-whitespace-docstring "a\n     b"))))

(deftest root-resource-test
  (is (= "/foo_bar_baz/boo/core" (planck.repl/root-resource 'foo-bar-baz.boo.core))))
