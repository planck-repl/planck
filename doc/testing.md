## Testing

It is possible to write and execute unit tests using Planck. 

Let's say you have a namespace with a function you'd like to test.
 
```clojure
(ns foo.core)

(defn square
  [x]
  (+ x x))
```

You can test `foo.core` by writing a test namespace:

```clojure
(ns foo.core-test
  (:require [cljs.test :refer-macros [deftest is]]
            [foo.core]))
            
(deftest test-square
  (is (= 0 (foo.core/square 0)))
  (is (= 9 (foo.core/square 3))))
```

Then you can run the unit tests using `run-tests`:

```clojure-repl
cljs.user=> (cljs.test/run-tests 'foo.core-test)

Testing foo.core-test

FAIL in (test-square) (:5:1)
expected: (= 9 (foo.core/square 3))
  actual: (not (= 9 6))

Ran 1 tests containing 2 assertions.
1 failures, 0 errors.
nil
```

If you fix the definition of `square` to make use of `*` instead of `+`, then you can run the tests again and see thing they pass:

```clojure-repl
cljs.user=> (cljs.test/run-tests 'foo.core-test)

Testing foo.core-test

Ran 1 tests containing 2 assertions.
0 failures, 0 errors.
nil
```

### Custom Asserts

The `cljs.test` library provides a mechanism for writing custom asserts that can be used with the `is` macro—in the form of an `assert-expr` `defmulti`.

To define your own assert, simply provide a `defmethod` for `cljs.test$macros/assert-expr`. Here's an example:

If you evaluate `(is (char? nil))` you will get a cryptic error report:

```
ERROR in () (isUnicodeChar@file:269:12)
expected: (char? nil)
  actual: #object[TypeError TypeError: null is not an object (evaluating 'ch.length')]
```

You can define a custom assert for this situation:

```clojure
(defmethod cljs.test$macros/assert-expr 'char? 
  [menv msg form]
  (let [arg    (second form)
        result (and (not (nil? arg))
                    (char? arg))]
    `(do
       (if ~result
         (cljs.test/do-report
           {:type     :pass
            :message  ~msg
            :expected '~form
            :actual   (list '~'char? ~arg)})
         (cljs.test/do-report
           {:type     :fail
            :message  ~msg
            :expected '~form
            :actual   (list '~'not 
                        (list '~'char? ~arg))}))
       ~result)))
```

With this, `(is (char? nil))` yields:

```
FAIL in () (eval@[native code]:NaN:NaN)
expected: (char? nil)
  actual: (not (char? nil))
```
