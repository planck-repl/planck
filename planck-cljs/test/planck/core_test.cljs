(ns planck.core-test
  (:require-macros
   [planck.core])
  (:require
   [clojure.test :refer [deftest is testing]]
   [foo.core]
   [planck.core]))

(deftest exit-throws
  #_(testing "exit throws EXIT exception"
      (is (thrown-with-msg? js/Error #"PLANCK_EXIT" (planck.core/exit 112))))
  #_(testing "exit sets global exit code"
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

(defrecord Foo [x]
  planck.core/IClosable
  (planck.core/-close [_] (println "Close" x)))

(deftest test-with-open
  (is (= "Body1\nBody2\nClose :y\nClose :x\n"
        (with-out-str
          (planck.core/with-open [x (Foo. :x)
                                  y (Foo. :y)]
            (println "Body1")
            (println "Body2"))))))

(defn f [x] (* x x x))

(def f-resolved (planck.core/resolve 'planck.core-test/f))

(deftest test-in-ns-REPL-special
  (is (= :planck.core-test/a ::a)))

(deftest test-resolve
  (is (= 3 ((planck.core/resolve '+) 1 2)))
  (is (= 27 ((planck.core/resolve 'planck.core-test/f) 3))))

(deftest test-ns-resolve
  (is (= 3 @(planck.core/ns-resolve 'foo.core 'h)))
  (is (= 3 (apply @(planck.core/ns-resolve 'cljs.core '+) [1 2]))))

(planck.core/intern 'foo.core 'a 3)
(planck.core/intern 'foo.core (with-meta 'b {:alpha 17}) 12)
(planck.core/intern (find-ns 'foo.core) 'd 18)

(deftest test-intern
  (is (= 4 (count (planck.core/eval '(ns-interns (quote foo.core))))))
  (is (= 12 @(planck.core/ns-resolve 'foo.core 'b)))
  (is (= 17 (:alpha (meta (planck.core/ns-resolve 'foo.core 'b)))))
  (is (= 18 @(planck.core/ns-resolve 'foo.core 'd))))

(defn spit-slurp [file-name content]
  (planck.core/spit file-name content)
  (planck.core/slurp file-name))

(deftest test-spit-slurp
  (let [test-file "/tmp/PLANCK_TEST.txt"]
    (is (= "" (spit-slurp test-file "")))
    (is (= "a" (spit-slurp test-file "a")))
    (is (= "a\n" (spit-slurp test-file "a\n")))
    (is (= "a\nb" (spit-slurp test-file "a\nb")))
    (is (= "a\nb\n" (spit-slurp test-file "a\nb\n")))))

(deftest init-empty-state-test
  (is (= {:ns 'cljs.user, :value '(2 3 4)}
        (cljs.js/eval-str (cljs.js/empty-state planck.core/init-empty-state)
          "(map inc [1 2 3])"
          nil
          {:eval cljs.js/js-eval}
          identity))))
