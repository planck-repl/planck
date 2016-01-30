(ns planck.io-test
  (:require [cljs.test :refer-macros [deftest testing is]]
            [planck.io]))

(deftest file-attributes-test
  (testing "file-attributes"
    (is (nil? (planck.io/file-attributes nil)))
    (is (nil? (planck.io/file-attributes "bogus")))
    (is (map? (planck.io/file-attributes "/tmp")))
    (is (keyword-identical? :symbolic-link (:type (planck.io/file-attributes "/tmp"))))
    (is (keyword-identical? :symbolic-link (:type (planck.io/file-attributes (planck.io/file "/tmp")))))
    (is (number? (:reference-count (planck.io/file-attributes "/tmp"))))
    (is (number? (:permissions (planck.io/file-attributes "/tmp"))))
    (is (number? (:file-number (planck.io/file-attributes "/tmp"))))
    (is (number? (:uid (planck.io/file-attributes "/tmp"))))
    (is (number? (:gid (planck.io/file-attributes "/tmp"))))
    (is (string? (:uname (planck.io/file-attributes "/tmp"))))
    (is (string? (:gname (planck.io/file-attributes "/tmp"))))
    (is (= js/Date (type (:created (planck.io/file-attributes "/tmp")))))
    (is (= js/Date (type (:modified (planck.io/file-attributes "/tmp")))))
    (is (number? (:creator-code (planck.io/file-attributes "/tmp"))))
    (is (number? (:type-code (planck.io/file-attributes "/tmp"))))
    (is (number? (:file-size (planck.io/file-attributes "/tmp"))))
    (let [extension-hidden (:extension-hidden (planck.io/file-attributes "/tmp"))]
      (is (or (true? extension-hidden)
              (false? extension-hidden))))))
