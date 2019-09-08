(ns general.transit-test
  (:require
   [clojure.test :refer [deftest is]]
   [cognitect.transit :as t]))

(deftest serialize-meta
  (let [roundtrip (fn [x]
                    (let [w (t/writer :json
                              {:transform t/write-meta})
                          r (t/reader :json)]
                      (t/read r (t/write w x))))
        x         ^:foo [1 2]]
    (is (= x (roundtrip x)))
    (is (= (meta x) (meta (roundtrip x))))))
