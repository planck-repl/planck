(ns planck.io-test
  (:require [cljs.test :refer-macros [deftest testing is]]
   [planck.io]
   [planck.core]))

(deftest file-attributes-test
  (testing "file-attributes"
    (is (nil? (planck.io/file-attributes nil)))
    (is (nil? (planck.io/file-attributes "bogus")))
    (is (map? (planck.io/file-attributes "/tmp")))
    (is (keyword-identical? :symbolic-link (:type (planck.io/file-attributes "/dev/stdout"))))
    (is (keyword-identical? :symbolic-link (:type (planck.io/file-attributes (planck.io/file "/dev/stdout")))))
    (is (keyword-identical? :character-special (:type (planck.io/file-attributes "/dev/null"))))
    (is (number? (:reference-count (planck.io/file-attributes "/tmp"))))
    (is (number? (:permissions (planck.io/file-attributes "/tmp"))))
    (is (number? (:file-number (planck.io/file-attributes "/tmp"))))
    (is (number? (:uid (planck.io/file-attributes "/tmp"))))
    (is (number? (:gid (planck.io/file-attributes "/tmp"))))
    (is (string? (:uname (planck.io/file-attributes "/tmp"))))
    (is (string? (:gname (planck.io/file-attributes "/tmp"))))
    (is (= js/Date (type (:created (planck.io/file-attributes "/tmp")))))
    (is (= js/Date (type (:modified (planck.io/file-attributes "/tmp")))))
    (is (number? (:file-size (planck.io/file-attributes "/tmp"))))))

(deftest coercions
  (testing "as-file coerceions"
    (is (nil? (planck.io/as-file nil)))
    (is (= (planck.io/File. "abc") (planck.io/as-file "abc")))
    (is (= (planck.io/File. "abc") (planck.io/as-file (planck.io/as-file "abc"))))
    (is (= "abc" (.-path (planck.io/as-file "abc"))))))

(deftest reader
  (testing "close"
    (planck.core/spit "/tmp/plnk-reader-test.txt" "Foo")
    (let [r (planck.io/reader "/tmp/plnk-reader-test.txt")]
      (planck.core/-close r)
      (is (thrown-with-msg? js/Error #"File closed" (planck.core/-read r))))))

(deftest writer
  (testing "close"
    (let [w (planck.io/writer "/tmp/plnk-writer-test.txt")]
      (planck.core/-close w)
      (is (thrown-with-msg? js/Error #"File closed" (cljs.core/-write w "hi"))))))

(deftest io-factory-on-std-streams-test
  (is (identical? *out* (planck.io/writer *out*)))
  (is (identical? planck.core/*err* (planck.io/writer planck.core/*err*)))
  (is (identical? planck.core/*in* (planck.io/reader planck.core/*in*))))

(deftest io-factory-on-std-streams-fail-test
  (is (thrown? js/Error (planck.io/reader *out*)))
  (is (thrown? js/Error (planck.io/reader planck.core/*err*)))
  (is (thrown? js/Error (planck.io/writer planck.core/*in*))))
