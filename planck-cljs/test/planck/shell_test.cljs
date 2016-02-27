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
