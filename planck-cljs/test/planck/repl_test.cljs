(ns planck.repl-test
  (:require-macros
   [planck.repl])
  (:require
   [clojure.test :refer [deftest is testing]]
   [clojure.test.check.clojure-test :refer-macros [defspec]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop :include-macros true]
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
  (is (= '(cljs.core/ffirst cljs.core/nfirst) (planck.repl/apropos #"[a-z]+first")))
  (is (= '(cljs.core/aget) (planck.repl/apropos "aget"))))

(deftest test-dir-planck-repl
  (is (= "*pprint-results*\napropos\ndir\ndoc\nfind-doc\nget-arglists\npst\nsource\n" (with-out-str (planck.repl/dir planck.repl)))))

(deftest get-error-indicator-test
  (is (= "             ^"
        (repl/get-error-column-indicator
          (ex-info repl/could-not-eval-expr {}
            (ex-info "" {:tag    :cljs/analysis-error
                         :column 3}))
          "foo.core"))))

(deftest undo-reader-conditional-whitespace-docstring-test
  (is (= "a\n  b" (planck.repl/undo-reader-conditional-whitespace-docstring "a\n     b"))))

(deftest root-resource-test
  (is (= "/foo_bar_baz/boo/core" (planck.repl/root-resource 'foo-bar-baz.boo.core))))

(defspec add-drop-macros-suffix-test
  (prop/for-all [ns-sym gen/symbol]
    (= ns-sym (-> ns-sym
                repl/add-macros-suffix
                str
                repl/drop-macros-suffix
                symbol))))

(deftest get-arglists-test
  (is (= '([x] [x y] [x y & more]) (planck.repl/get-arglists "max")))
  (is (nil? (planck.repl/get-arglists "bogus-undefined"))))

(defn is-completion [i o]
  (let [completions (planck.repl/get-completions i)]
    (is (= (js->clj completions) (sort (into [] (map str) o))))))

(defn is-contains-completion
  ([i o]
   (is-contains-completion i o identity))
  ([i o f]
   (let [completions (planck.repl/get-completions i)]
     (is (f (contains? (set completions) o))))))

(deftest test-get-completions
  (testing "keyword completions"
    (is-completion ":" planck.repl/keyword-completions)
    (is-completion ":a" [":args" ":as"])
    (is-completion ":ref" [":refer" ":refer-clojure" ":refer-macros"]))
  (testing "aliased namespaces completions"
    (with-redefs [planck.repl/current-alias-map (fn []
                                                  '{string clojure.string})]
      (is-contains-completion "str" "string/")
      (is-contains-completion "(str" "(string/")
      (is-contains-completion "(set" "(set/" not))
    (with-redefs [planck.repl/all-ns (fn [] '(clojure.set clojure.string))]
      (is-contains-completion "(clojure.s" "(clojure.set")))
  (testing "cljs.core function completions"
    (is-contains-completion "sub" "subs")
    (is-contains-completion "mer" "merge")
    (is-contains-completion "clojure.core/mer" "clojure.core/merge"))
  #_(testing "referred vars completions"
      (with-redefs [planck.repl/get-namespace (fn [_]
                                                '{:uses       {foo foo.core}
                                                  :requires   {foo.core foo.core}
                                                  :use-macros {longer-var bar.core}})]
        (is-contains-completion "fo" "foo")
        (is-contains-completion "lon" "longer-var")
        (is-contains-completion "(lon" "(longer-var")))
  (testing "completions after slash"
    (is-contains-completion "clojure.core/" "clojure.core/merge")
    (is-contains-completion "" "merge")
    (with-redefs [planck.repl/current-alias-map (fn []
                                                  '{string clojure.string})]
      (is-contains-completion "(string/" "(string/merge" not)))
  #_(testing "JS Completions"
      (is-contains-completion "js/con" "js/console"))
  (testing "Auto-complete after arrow and other special chars"
    (is-contains-completion "(-" "(->")
    (is-contains-completion "(-" "(->>")
    (is-contains-completion "(->" "(->>")
    (is-contains-completion "(->" "(->merge" not)
    (is-contains-completion "*" "*clojurescript-version*")
    (is-contains-completion "*" "*merge" not)
    (is-contains-completion "(<" "(<merge" not)
    (is-contains-completion "(=" "(=merge" not)
    (is-contains-completion "(&" "(&merge" not)
    (is-contains-completion "(?" "(?merge" not)
    (is-contains-completion "(/" "(/merge" not)
    (is-contains-completion "(?" "(?merge" not)
    (is-contains-completion "(MER" "(merge")))

(deftest completions-for-goog-ns
  (is (some #{"isArrayLike"} (planck.repl/completion-candidates-for-ns 'goog false)))
  (is (some #{"trimLeft"} (planck.repl/completion-candidates-for-ns 'goog.string false))))

(deftest doc-test
  (is (empty? (with-out-str (planck.repl/doc every)))))

(deftest planck-native?-test
  (is (planck.repl/planck-native? 'PLANCK_SOCKET_CLOSE))
  (is (not (planck.repl/planck-native? 'foobar))))

(deftest gensym?-test
  (is (planck.repl/gensym? (gensym)))
  (is (not (planck.repl/gensym? 'foobar))))

(deftest test-is-readable?
  (is (= (planck.repl/is-readable? "(") nil))
  (is (= (planck.repl/is-readable? "(+ 1 2)") ""))
  (is (= (planck.repl/is-readable? "(+ 1 2) :foo") " :foo"))
  (is (= (planck.repl/is-readable? "") nil))
  (is (= (planck.repl/is-readable? ")") "")))

(deftest repl-read-string-test
  (is (thrown? js/Error (planck.repl/repl-read-string "34f")))
  (is (thrown? js/Error (planck.repl/repl-read-string "a:")))
  (is (thrown? js/Error (planck.repl/repl-read-string "]"))))
