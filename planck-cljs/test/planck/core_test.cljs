(ns planck.core-test
  (:require-macros
   [planck.core])
  (:require
   [clojure.test :refer [deftest is are testing]]
   [clojure.string :as string]
   [foo.core]
   [planck.core]
   [clojure.string :as string])
  (:import
   (goog Uri)))

(deftest exit-throws
  #_(testing "exit throws EXIT exception"
      (is (thrown-with-msg? js/Error #"PLANCK_EXIT" (planck.core/exit 112))))
  #_(testing "exit sets global exit code"
      (is (= 112 (js/PLANCK_GET_EXIT_VALUE)))))

#_(deftest setTimeout-can-be-used
    "This test is intentionally a bit naieve since async macros wouldn't work at time of writing"
    (testing "setTimeout actually gets called and does something"
      (let [now        #(.getTime (js/Date.))
            t          (now)
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

(deftest slurp-url-test
  (is (string/includes? (planck.core/slurp "http://planck-repl.org") "Planck")))

(deftest slurp-from-jar-test
  (is (= "(ns test-jar.core)\n\n(def x \"Hello, from JAR\")\n"
        (planck.core/slurp
          (Uri. "jar:file:int-test/test-jar.jar!/test_jar/core.cljs"))))
  (is (thrown? js/Error (planck.core/slurp
                          (Uri. "jar:file:int-test/test-jar.jar!/bogus_path/core.cljs"))))
  (is (thrown? js/Error (planck.core/slurp
                          (Uri. "jar:file:bogus-path/test-jar.jar!/test_jar/core.cljs"))))
  (is (thrown? js/Error (planck.core/slurp
                          (Uri. "jar:non-file:int-test/test-jar.jar!/test_jar/core.cljs")))))

(deftest buffered-reader-test
  (let [read-count      (volatile! 0)
        raw-read        #(let [rv (case @read-count
                                    0 "ab"
                                    1 "c\nd"
                                    2 "ef"
                                    3 "\n"
                                    4 nil)]
                           (vswap! read-count inc)
                           rv)
        buffered-reader (planck.core/make-raw-pushback-reader raw-read #() (atom nil) (atom 0))]
    (is (= "abc" (planck.core/-read-line buffered-reader)))
    (is (= "def" (planck.core/-read-line buffered-reader)))
    (is (nil? (planck.core/-read-line buffered-reader)))))

(deftest sleep-test
  (let [before (system-time)
        _      (planck.core/sleep 10)
        after  (system-time)]
    (is (> (- after before) 8)))
  (let [before (system-time)
        _      (planck.core/sleep 0 100000)
        after  (system-time)]
    (is (> (- after before) 0.08))))

(deftest read-string-test
  (is (= 1.1 (planck.core/read-string "1.1") ))
  (is (thrown? js/Error (planck.core/read-string "1.1.1 (+ 1 1)")))
  (is (= '(+ 1 1) (planck.core/read-string "(+ 1 1)")))
  (is (= 5 (planck.core/read-string "; foo\n5")))
  (is (= 'x (planck.core/read-string "#^String x")))
  (is (= '(1) (planck.core/read-string "(1)")))
  (is (= '(+ 1 2) (planck.core/read-string "(+ 1 2) (- 3 2)")))
  (is (= '(clojure.core/deref a) (planck.core/read-string "@a")))
  (is (= '(+ 1 2) (planck.core/read-string "(+ 1 2))))))")))
  (is (= '(\( \x \y \) \z) (planck.core/read-string "(\\( \\x \\y \\) \\z)")))
  (is (= 11 (planck.core/read-string (str "2r" "1011")))))

(deftest tree-seq-test
  (are [branch? children root] (= (planck.core/tree-seq branch? children root)
                                  (tree-seq branch? children root))
    seq? identity '((1 2 (3)) (4))
    map? #(interleave (keys %) (vals %)) {:a 1 :b {:c 3 :d 4 :e {:f 6 :g 7}}}
    next rest '(:A (:B (:D) (:E)) (:C (:F)))
    sequential? seq [[1 2 [3]] [4]]))

(deftest iterate-seq-test
  (is (= "nil" (planck.core/iterate-seq pr-str (constantly 3) nil)))
  (is (= [0 1 2] (take 3 (planck.core/iterate-seq first (fn [[x]] [(inc x)]) [0]))))
  (is (= [0 1] (planck.core/iterate-seq first (fn [[x]] (when (zero? x) [(inc x)])) [0])))
  (is (= 1 (reduce + 0 (planck.core/iterate-seq first (fn [[x]] (when (zero? x) [(inc x)])) [0]))))
  (is (= 1 (reduce + (planck.core/iterate-seq first (fn [[x]] (when (zero? x) [(inc x)])) [0]))))
  (is (realized? (planck.core/iterate-seq first (fn [[x]] [(inc x)]) [0])))
  (is (not (realized? (rest (planck.core/iterate-seq first (fn [[x]] [(inc x)]) [0])))))
  (is (= {:a 1} (meta (with-meta (planck.core/iterate-seq first (fn [[x]] [(inc x)]) [0]) {:a 1}))))
  (is (= [:y :x 0 1] (conj (planck.core/iterate-seq first (fn [[x]] (when (zero? x) [(inc x)])) [0]) :x :y)))
  (let [v (empty (with-meta (planck.core/iterate-seq first (fn [[x]] [(inc x)]) [0]) {:a 1}))]
    (is (= () v))
    (is (= {:a 1} (meta v)))))

(deftest line-seq-test
  (are [xs s] (= xs (planck.core/line-seq (planck.core/make-string-reader s)))
    nil ""
    ("a") "a"
    ("a" "b") "a\nb"
    ("a" "b") "a\nb\n"
    ("a" "b" "c") "a\nb\nc"))
