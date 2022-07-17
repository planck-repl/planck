(ns planck.core-test
  (:require-macros
   [planck.core])
  (:require
   [clojure.test :refer [deftest is testing]]
   [clojure.math :as math]
   [clojure.string :as string]
   [foo.core]
   [planck.core]
   [some-arbitrary.namespace.symbol :as my-alias]
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
(planck.core/intern (find-ns 'foo.core) 'c 18)
(planck.core/intern 'foo.core 'd 'bar)
(planck.core/intern 'foo.core 'e '[bar])

(deftest test-intern
  (is (= 6 (count (planck.core/eval '(ns-interns (quote foo.core))))))
  (is (= 12 @(planck.core/ns-resolve 'foo.core 'b)))
  (is (= 17 (:alpha (meta (planck.core/ns-resolve 'foo.core 'b)))))
  (is (= 18 @(planck.core/ns-resolve 'foo.core 'c)))
  (is (= 'bar @(planck.core/ns-resolve 'foo.core 'd)))
  (is (= '[bar] @(planck.core/ns-resolve 'foo.core 'e))))

(deftest test-ns-aliases
  (is (= '{string clojure.string, set clojure.set}
        (into {} (map (fn [[k v]] [k (ns-name v)])) (planck.core/ns-aliases 'foo.core))))
  (is (thrown? js/Error (planck.core/ns-aliases 'unknown.namespace))))

(deftest test-ns-refers
  (let [refers (planck.core/ns-refers 'foo.core)]
    (is (= ['union #'clojure.set/union] (find refers 'union)))
    (is (= ['intersection #'clojure.set/intersection] (find refers 'intersection)))
    (is (= ['reduce #'cljs.core/reduce] (find refers 'reduce)))
    (is (not (contains? refers 'map)))))

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
  (cljs.js/eval-str (cljs.js/empty-state planck.core/init-empty-state)
    "(map inc [1 2 3])"
    nil
    {:eval cljs.js/js-eval}
    (fn [{:keys [ns value error]}]
      (is (nil? error))
      (is (= 'cljs.user ns))
      (is (= '(2 3 4) value)))))

(deftest slurp-url-test
  (is (string/includes? (planck.core/slurp "https://planck-repl.org") "Planck")))

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
        buffered-reader (#'planck.core/->Reader raw-read #() (atom nil) (atom 0))]
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

(deftest find-var-test
  (is (nil? (planck.core/find-var 'cljs.core/nonexist)))
  (is (= #'cljs.core/filter (planck.core/find-var 'cljs.core/filter))))

(deftest load-string-test
  (is (= 3 (planck.core/load-string "1 2 3")))
  (is (= "hi" (with-out-str (planck.core/load-string "1 (print \"hi\") 2"))))
  (is (= :foo.core/x (planck.core/load-string "(ns foo.core) ::x"))))

(deftest load-reader-test
  (is (= 3 (planck.core/load-reader (#'planck.core/make-string-reader "1 2 3")))))

(deftest requiring-resolve-test
  (is (nil? (planck.core/resolve 'planck.requiring-resolve-ns/a)))
  (is (= 3 @(planck.core/requiring-resolve 'planck.requiring-resolve-ns/a))))

(deftest with-in-str-test
  (is (= "34" (planck.core/with-in-str "34\n35" (planck.core/read-line)))))

(deftest slurp-follow-redirects-by-default
  (is (string/includes? (planck.core/slurp "http://planck-repl.org") "ClojureScript REPL"))
  (is (string/includes? (planck.core/slurp "http://planck-repl.org" :follow-redirects false) "Moved Permanently")))

(deftest clojure-math-test
  (is (== 1 (math/floor 1.5))))

(deftest as-alias-test
  (is (= :some-arbitrary.namespace.symbol/x ::my-alias/x)))
