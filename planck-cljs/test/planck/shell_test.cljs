(ns planck.shell-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as string]
            [planck.io]
            [planck.shell :include-macros true]))

(deftest shell
  (is (= #{[:err ""] [:exit 0] [:out "hello\n"]}
          (into (sorted-set) (planck.shell/sh "echo" "hello")))))

(deftest shell-throws
  (is (thrown-with-msg? js/Error
                        #"launch path not accessible"
                        (planck.shell/sh "bogus"))))

(deftest capture-exit-value
  (is (= 0 (:exit (planck.shell/sh "sh" "-c" "exit 0"))))
  (is (= 1 (:exit (planck.shell/sh "sh" "-c" "exit 1"))))
  (is (= 17 (:exit (planck.shell/sh "sh" "-c" "exit 17")))))

(deftest with-sh-dir-test
  (is (string/ends-with?
        (:out (planck.shell/with-sh-dir "script"
                (planck.shell/sh "pwd")))
        "script\n"))
  (is (string/ends-with?
        (:out (planck.shell/with-sh-dir (planck.io/file "script")
                (planck.shell/sh "pwd")))
        "script\n"))
  (let [rv (planck.shell/with-sh-dir "bogus"
             (planck.shell/sh "pwd"))]
    (is (not= 0 (:exit rv)))))

(deftest with-sh-env-test
  (is (= "FOO=BAR\n" (:out (planck.shell/with-sh-env {"FOO" "BAR"}
                             (planck.shell/sh "env"))))))
