(ns planck.repl-test
  (:require-macros
   [planck.repl])
  (:require
   [clojure.spec.alpha :as s]
   [clojure.test :refer [deftest is testing]]
   [clojure.test.check.clojure-test :refer-macros [defspec]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop :include-macros true]
   [goog :as g]
   [planck.repl :as repl]))

(deftest get-highlight-coords
  (testing "Highlight coordinates"
    (is (= [0 0] (js->clj (#'planck.repl/get-highlight-coords 1 "[]" []))))
    (is (= [0 1] (js->clj (#'planck.repl/get-highlight-coords 2 "[[]]" []))))
    (is (= [0 0] (js->clj (#'planck.repl/get-highlight-coords 3 "[()]" []))))
    (is (= [0 1] (js->clj (#'planck.repl/get-highlight-coords 2 " []" []))))
    (is (= [1 0] (js->clj (#'planck.repl/get-highlight-coords 1 "]" ["["]))))
    (is (= [2 0] (js->clj (#'planck.repl/get-highlight-coords 1 "]" ["[" ""]))))
    (is (= [1 0] (js->clj (#'planck.repl/get-highlight-coords 1 "]" ["[" "["]))))
    (is (= [2 0] (js->clj (#'planck.repl/get-highlight-coords 1 "]" ["[" "[]"]))))
    (is (= [0 1] (js->clj (#'planck.repl/get-highlight-coords 2 "#{}" []))))
    (is (= [0 0] (js->clj (#'planck.repl/get-highlight-coords 4 "[\"[\"]" []))))))

(deftest test-apropos
  (is (= '(cljs.core/ffirst) (planck.repl/apropos "ffirst")))
  (is (= '(cljs.core/ffirst) (planck.repl/apropos ffirst)))
  (is (= '(cljs.core/ffirst cljs.core/nfirst) (planck.repl/apropos #"[a-z]+first")))
  (is (= '(cljs.core/aget) (planck.repl/apropos "aget"))))

(deftest test-dir-planck-repl
  (is (= "*pprint-results*\napropos\napropos*\ndir\ndir*\ndoc\ndoc*\nfind-doc\nfind-doc*\nget-arglists\npst\npst*\nsource\nsource*\n"
        (with-out-str (planck.repl/dir planck.repl)))))

(deftest get-error-indicator-test
  (is (= "             ^"
        (#'repl/get-error-column-indicator
          (ex-info @#'repl/could-not-eval-expr {}
            (ex-info "" {:tag    :cljs/analysis-error
                         :column 3}))
          "foo.core"))))

(deftest undo-reader-conditional-whitespace-docstring-test
  (is (= "a\n  b" (#'planck.repl/undo-reader-conditional-whitespace-docstring "a\n     b"))))

(deftest root-resource-test
  (is (= "/foo_bar_baz/boo/core" (#'planck.repl/root-resource 'foo-bar-baz.boo.core))))

(defspec add-drop-macros-suffix-test
  (prop/for-all [ns-sym gen/symbol]
    (= ns-sym (-> ns-sym
                (#'repl/add-macros-suffix)
                str
                (#'repl/drop-macros-suffix)
                symbol))))

(deftest get-arglists-test
  (is (= '([x] [x y] [x y & more]) (#'planck.repl/get-arglists "max")))
  (is (nil? (#'planck.repl/get-arglists "bogus-undefined"))))

(deftest completions-for-goog-ns
  (is (some #{"isArrayLike"} (#'planck.repl/completion-candidates-for-ns 'goog false)))
  (is (some #{"trimLeft"} (#'planck.repl/completion-candidates-for-ns 'goog.string false))))

(deftest doc-test
  (is (empty? (with-out-str (planck.repl/doc every)))))

(deftest planck-native?-test
  (is (#'planck.repl/planck-native? 'PLANCK_SOCKET_CLOSE))
  (is (not (#'planck.repl/planck-native? 'foobar))))

(deftest gensym?-test
  (is (#'planck.repl/gensym? (gensym)))
  (is (not (#'planck.repl/gensym? 'foobar))))

(deftest test-is-readable?
  (is (= (#'planck.repl/is-readable? "(") nil))
  (is (= (#'planck.repl/is-readable? "(+ 1 2)") ""))
  (is (= (#'planck.repl/is-readable? "(+ 1 2) :foo") " :foo"))
  (is (= (#'planck.repl/is-readable? "") nil))
  (is (= (#'planck.repl/is-readable? ")") "")))

(deftest repl-read-string-test
  (is (thrown? js/Error (#'planck.repl/repl-read-string "34f")))
  (is (thrown? js/Error (#'planck.repl/repl-read-string "a:")))
  (is (thrown? js/Error (#'planck.repl/repl-read-string "]")))
  (try
    (#'planck.repl/repl-read-string "34f")
    (catch :default e
      (is (= :read-source (:clojure.error/phase (ex-data e)))))))

(deftest strip-source-map-test
  (let [input-sm {0 {2 [{:line 1 :col 1} {:line 2 :col 2 :name "a"}]
                     3 [{:line 2 :col 2 :name "b"}]}}
        stripped {0 {2 [{:line 2 :col 2}]}}]
    (is (= stripped (#'planck.repl/strip-source-map input-sm)))))

(deftest require-goog-test
  (is (false? (g/isArrayLike nil))))

(deftest issue-749-test
  (let [source "#!/usr/bin/env bash\n\"exec\" \"plk\" \"-Sdeps\" \"{:deps {org.clojure/tools.cli {:mvn/version \\\"0.3.7\\\"}}}\" \"-Ksf\" \"$0\" \"$@\"\n\n(ns repro.core\n  (:require [clojure.tools.cli :refer [parse-opts]]))"]
    (is (= 'repro.core (#'planck.repl/extract-namespace source))))
  (let [source "#!/usr/bin/env bash\n\"exec\" \"plk\" \"-Sdeps\" \"{:deps {org.clojure/tools.cli {:mvn/version \\\"0.3.7\\\"}}}\" \"-Ksf\" \"$0\" \"$@\"\n\n(require '[clojure.tools.cli :refer [parse-opts]])"]
    (is (= 'cljs.user (#'planck.repl/extract-namespace source))))
  (let [source ":hello"]
    (is (= 'cljs.user (#'planck.repl/extract-namespace source)))))

(deftest read-compile-optss-test
  (let [compile-optss #js ["{:a 1}" "@co0.edn" "{:b 2}" "@/co1.edn:@co2.edn" "{:a 3}"]]
    (is (=  {:a 3, :b 17, :test1 :t, :test2 :z, :test3 :y, :test4 :j, :test5 :h}
          (#'planck.repl/read-compile-optss compile-optss)))))

(defn my-int? [x] (int? x))

(deftest spec-describe-core-fns
  (is (= 'my-int? (s/describe my-int?)))
  (is (= 'int? (s/describe int?))))
