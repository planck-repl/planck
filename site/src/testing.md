## Testing

It is possible to write and execute unit tests using Planck. 

> Planck ships with a copy of `cljs.test` which has been ported for use with bootstrap ClojureScript.

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

FAIL in (test-square) (cljs$test$run_block@cljs/test.cljs:389:88)
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