(ns planck.shell-test
  (:require
   [clojure.string :as string]
   [clojure.test :refer [deftest is]]
   [planck.io]
   [planck.shell :include-macros true]))

(deftest shell
  (is (= #{[:err ""] [:exit 0] [:out "hello\n"]}
        (into (sorted-set) (planck.shell/sh "echo" "hello")))))

(deftest shell-throws
  (is (thrown-with-msg? js/Error
        #"Launch path \"bogus\" not accessible."
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

(deftest specify-dir-test
  (is (string/ends-with?
        (:out (planck.shell/sh "pwd" :dir "script"))
        "script\n"))
  (is (string/ends-with?
        (:out (planck.shell/sh "pwd" :dir (planck.io/file "script")))
        "script\n"))
  (let [rv (planck.shell/sh "pwd" :dir "bogus")]
    (is (not= 0 (:exit rv)))))

(deftest inline-env-test
  (is (= "FOO=BAR\n" (:out (planck.shell/sh "env" :env {"FOO" "BAR"})))))

(deftest with-sh-env-test
  (is (= "FOO=BAR\n" (:out (planck.shell/with-sh-env {"FOO" "BAR"}
                             (planck.shell/sh "env"))))))

(deftest with-sh-env-throws-on-nil-env-vars-test
  (is (thrown-with-msg? js/Error
                        #"fails spec"
                        (planck.shell/with-sh-env {nil "value-for-a-nil-key"}
                          (planck.shell/sh "env"))))
  (is (thrown-with-msg? js/Error
                        #"fails spec"
                        (planck.shell/with-sh-env {"key-with-a-nil-value" nil}
                          (planck.shell/sh "env")))))

(deftest launch-fail-ex-info-test
  (try
    (planck.shell/sh "env" "abc")
    (catch :default e
      (let [expected-errors #{"env: abc: No such file or directory"
                              "env: ‘abc’: No such file or directory"}]
        (is (= 127 (:exit (ex-data e))))
        (is (= "" (:out (ex-data e))))
        (is (contains? expected-errors (string/trim (:err (ex-data e)))))
        (is (contains? expected-errors (ex-message e)))))))

(deftest launch-fail-msg-test
  (is (= "Launch path \"ls -l\" not accessible. Did you perhaps mean to launch using \"ls\", with (\"-l\") as arguments?"
        (#'planck.shell/launch-fail-msg "ls -l"))))
