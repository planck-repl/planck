(ns planck.environ-test
  (:require [planck.environ :refer env]
            [planck.io :as io]
            [clojure.string :as string]
            [cljs.test :refer-macros [deftest is use-fixtures]]))

(deftest user-home
  (is (and (#{"HOME"} (keys env))
           (not (string/blank? (get env "HOME")))
           (io/directory? (get env "HOME")))))
