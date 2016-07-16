(ns planck.shell-test
  (:require [cljs.test :refer-macros [deftest is]]
            [planck.shell]))

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
