(ns planck.io-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [clojure.string :as string]
   [planck.core :refer [spit slurp with-open]]
   [planck.io :as io]
   [planck.shell :as shell])
  (:import
   (goog Uri)))

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

(deftest predicates-test
  (let [regular-file      "/tmp/plk-predicates-file.txt"
        regular-directory "/tmp/plk-directory/"
        hidden-file       "/tmp/.plk-predicates-hidden-file.txt"
        hidden-directory "/tmp/.plk-hidden-directory/"]
    (spit regular-file "Planck test case: regular file")
    (io/make-parents regular-directory)
    (spit hidden-file "Planck test case: hidden file")
    (io/make-parents hidden-directory)
    (testing "predicates"
      (is (= false (planck.io/directory? "bogus")))
      (is (= false (planck.io/exists? "bogus")))
      (is (= false (planck.io/hidden-file? "bogus")))
      (is (= false (planck.io/regular-file? "bogus")))
      (is (= false (planck.io/symbolic-link? "bogus")))
      (is (= false (planck.io/directory? ".bogus")))
      (is (= false (planck.io/exists? ".bogus")))
      (is (= true  (planck.io/hidden-file? ".bogus")))
      (is (= false (planck.io/regular-file? ".bogus")))
      (is (= false (planck.io/symbolic-link? ".bogus")))
      (is (= true  (planck.io/directory? regular-directory)))
      (is (= true  (planck.io/exists? regular-directory)))
      (is (= false (planck.io/hidden-file? regular-directory)))
      (is (= false (planck.io/regular-file? regular-directory)))
      (is (= false (planck.io/symbolic-link? regular-directory)))
      (is (= false (planck.io/directory? "/dev/stdout")))
      (is (= true  (planck.io/exists? "/dev/stdout")))
      (is (= false (planck.io/hidden-file? "/dev/stdout")))
      (is (= false (planck.io/regular-file? "/dev/stdout")))
      (is (= true  (planck.io/symbolic-link? "/dev/stdout")))
      (is (= false (planck.io/directory? regular-file)))
      (is (= true  (planck.io/exists? regular-file)))
      (is (= false (planck.io/hidden-file? regular-file)))
      (is (= true  (planck.io/regular-file? regular-file)))
      (is (= false (planck.io/symbolic-link? regular-file)))
      (is (= false (planck.io/directory? hidden-file)))
      (is (= true  (planck.io/exists? hidden-file)))
      (is (= true  (planck.io/hidden-file? hidden-file)))
      (is (= true  (planck.io/regular-file? hidden-file)))
      (is (= false (planck.io/symbolic-link? hidden-file)))
      (is (= true  (planck.io/directory? hidden-directory)))
      (is (= true  (planck.io/exists? hidden-directory)))
      (is (= true  (planck.io/hidden-file? hidden-directory)))
      (is (= false (planck.io/regular-file? hidden-directory)))
      (is (= false (planck.io/symbolic-link? hidden-directory)))
      (is (boolean? (planck.io/tty? 0)))
      (is (boolean? (planck.io/tty? 1)))
      (is (boolean? (planck.io/tty? 2)))
      (is (boolean? (planck.io/tty? planck.core/*in*)))
      (is (boolean? (planck.io/tty? *out*)))
      (is (boolean? (planck.io/tty? planck.core/*err*)))
      (is (= false (planck.io/tty? nil)))
      (is (= false (planck.io/tty? -1)))
      (is (= false (planck.io/tty? 99999999999999999)))
      (is (= false (planck.io/tty? (planck.io/reader regular-file)))))))

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

(deftest bad-file-descriptors
  (is (thrown? js/Error (planck.io/reader "nonexistent")))
  (is (thrown? js/Error (planck.io/writer "/tmp")))
  (is (thrown? js/Error (planck.io/input-stream "nonexistent")))
  (is (thrown? js/Error (planck.io/output-stream "/tmp"))))

(deftest resource-test
  (is (nil? (planck.io/resource nil)))
  (is (nil? (planck.io/resource "/bogus/path")))
  (testing "file resources"
    (let [resource (planck.io/resource "foo/core.cljs")]
      (is (instance? Uri resource))
      (is "file" (.getScheme resource))
      (is (string/includes? (planck.core/slurp resource) "ns foo.core"))))
  (testing "JAR resources"
    (let [resource (planck.io/resource "clojure/test/check.cljc")]
      (is (instance? Uri resource))
      (is "jar" (.getScheme resource))
      (is (string/includes? (planck.core/slurp resource) "ns clojure.test.check"))))
  (testing "bundled resources"
    (let [resource (planck.io/resource "planck/repl.clj")]
      (is (instance? Uri resource))
      (is "bundled" (.getScheme resource))
      (is (string/includes? (planck.core/slurp resource) "ns planck.repl")))))

(deftest as-relative-path-test
  (is (= "a/b/c" (planck.io/as-relative-path "a/b/c")))
  (is (= "a/b/c" (planck.io/as-relative-path (planck.io/file "a/b/c"))))
  (is (thrown? js/Error (planck.io/as-relative-path (planck.io/file "/a/b/c")))))

(deftest file-test
  (is (= (planck.io/file "/tmp") (planck.io/file (planck.io/file "/tmp"))))
  (is (= (planck.io/file "/a/b") (planck.io/file "/a" "b")))
  (is (= (planck.io/file "/a/b") (planck.io/file (planck.io/file "/a") (planck.io/file "b"))))
  (is (= (planck.io/file "/a/b") (planck.io/file "/a" (planck.io/file "b"))))
  (is (= (planck.io/file "/a/b") (planck.io/file (planck.io/file "/a") "b")))
  (is (= (planck.io/file "/a/b/c") (planck.io/file "/a" "b" "c"))))

(deftest copy-test
  (let [content       (apply str (repeat 6754 "abcñdef\nafÈ§sdadsf\nταБЬℓσ\u1234fdsa\n"))
        src           "/tmp/plk-copy-src.txt"
        dst           "/tmp/plk-copy-dst.txt"
        no-diff       (fn [src dst]
                        (zero? (:exit (shell/sh "diff" src dst))))]
    (spit src content)
    (testing "InputStream -> OutputStream"
      (with-open [in (io/input-stream src)
                  out (io/output-stream dst)]
        (io/copy in out))
      (is (no-diff src dst)))
    (testing "InputStream -> Writer"
      (with-open [in (io/input-stream src)
                  out (io/writer dst)]
        (io/copy in out))
      (is (no-diff src dst)))
    (testing "InputStream -> File"
      (with-open [in (io/input-stream src)]
        (io/copy in (io/file dst)))
      (is (no-diff src dst)))
    (testing "Reader -> OutputStream"
      (with-open [in (io/reader src)
                  out (io/output-stream dst)]
        (io/copy in out))
      (is (no-diff src dst)))
    (testing "Reader -> Writer"
      (with-open [in (io/reader src)
                  out (io/writer dst)]
        (io/copy in out))
      (is (no-diff src dst)))
    (testing "Reader -> File"
      (with-open [in (io/reader src)]
        (io/copy in (io/file dst)))
      (is (no-diff src dst)))
    (testing "File -> OutputStream"
      (with-open [out (io/output-stream dst)]
        (io/copy (io/file src) out))
      (is (no-diff src dst)))
    (testing "File -> Writer"
      (with-open [out (io/writer dst)]
        (io/copy (io/file src) out))
      (is (no-diff src dst)))
    (testing "File -> File"
      (io/delete-file dst)
      (io/copy (io/file src) (io/file dst))
      (is (no-diff src dst))
      (io/copy (io/file src) (io/file dst))
      (is (no-diff src dst)))
    (testing "String -> OutputStream"
      (with-open [out (io/output-stream dst)]
        (io/copy content out))
      (is (no-diff src dst)))
    (testing "String -> Writer"
      (with-open [out (io/writer dst)]
        (io/copy content out))
      (is (no-diff src dst)))
    (testing "String -> File"
      (io/copy content (io/file dst))
      (is (no-diff src dst)))))

(deftest list-files-test
  (is (nil? (io/list-files "/bogus/path")))
  (is (seq? (io/list-files "/tmp")))
  (is (io/file? (first (io/list-files "/tmp")))))

(deftest temp-file-test
  (is (io/file? (io/temp-file))))

(deftest temp-directory-test
  (is (io/file? (io/temp-directory)))
  (is (io/directory? (io/temp-directory))))
