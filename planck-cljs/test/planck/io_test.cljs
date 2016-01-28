(ns planck.io-test
  (:require [cljs.test :refer-macros [deftest testing is]]
            [planck.io]))

(deftest fstat-test
  (testing "fstat"
    (is (nil? (planck.io/fstat nil)))
    (is (nil? (planck.io/fstat "bogus")))
    (is (map? (planck.io/fstat "/tmp")))
    (is (keyword-identical? :symboliclink (:type (planck.io/fstat "/tmp"))))
    (is (keyword-identical? :symboliclink (:type (planck.io/fstat (planck.io/file "/tmp")))))
    (is (number? (:referencecount (planck.io/fstat "/tmp"))))
    (is (number? (:permissions (planck.io/fstat "/tmp"))))
    (is (number? (:filenumber (planck.io/fstat "/tmp"))))
    (is (number? (:uid (planck.io/fstat "/tmp"))))
    (is (number? (:gid (planck.io/fstat "/tmp"))))
    (is (string? (:uname (planck.io/fstat "/tmp"))))
    (is (string? (:gname (planck.io/fstat "/tmp"))))
    (is (= js/Date (type (:created (planck.io/fstat "/tmp")))))
    (is (= js/Date (type (:modified (planck.io/fstat "/tmp")))))
    (is (number? (:creatorcode (planck.io/fstat "/tmp"))))
    (is (number? (:typecode (planck.io/fstat "/tmp"))))
    (is (number? (:filesize (planck.io/fstat "/tmp"))))
    (let [extension-hidden (:extensionhidden (planck.io/fstat "/tmp"))]
      (is (or (true? extension-hidden)
              (false? extension-hidden))))))
