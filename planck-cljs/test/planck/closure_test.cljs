(ns planck.closure-test
  (:require
   [clojure.test :refer [deftest is]]
   [planck.closure :as closure]))

(deftest compilation
  (let [source "function foo$core$square_inc(long_variable){return ((long_variable * long_variable) + (1));}"]
    (is (= {:source "function foo$core$square_inc(long_variable){return long_variable*long_variable+1};"}
          (closure/compile {:name "test" :source source :optimizations :whitespace})))
    (is (= {:source "function foo$core$square_inc(a){return a*a+1};"}
          (closure/compile {:name "test" :source source :optimizations :simple})))))

(deftest source-map
  (let [input             {:name          foo.core, :source "goog.provide(\"foo.core\");\nfoo.core.x = (1);",
                           :sm-data       {:source-map {2 {0 [{:gcol 0, :gline 1} {:gcol 13, :gline 1}],
                                                           5 [{:gcol 0, :gline 1, :name "foo.core/x"}]}},
                                           :gen-col    0,
                                           :gen-line   2}
                           :optimizations :whitespace}
        output-source-map {0 {24 [{:line 2, :col 0, :name "foo"} {:line 2, :col 5, :name "foo"}],
                              28 [{:line 2, :col 0, :name "core"} {:line 2, :col 5, :name "core"}],
                              33 [{:line 2, :col 0, :name "x"} {:line 2, :col 5, :name "x"}],
                              35 [{:line 2, :col 0, :name nil} {:line 2, :col 5, :name nil}]}}]
    (is (= output-source-map (:source-map (closure/compile input))))))
