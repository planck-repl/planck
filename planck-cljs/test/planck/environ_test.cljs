(ns planck.environ-test
  (:require [planck.environ :refer env]
            [planck.io :as io]
            [clojure.string :as string]
            [cljs.test :refer-macros [deftest is use-fixtures]]))

(deftest user-home
  (is (and (#{:home} (keys env))
           (not (string/blank? (:home env)))
           (io/directory? (:home env)))))
