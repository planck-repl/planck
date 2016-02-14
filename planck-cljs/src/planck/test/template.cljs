(ns planck.test.template
  (:require [clojure.walk :as walk]))

(defn apply-template
  "For use in macros.  argv is an argument list, as in defn.  expr is
  a quoted expression using the symbols in argv.  values is a sequence
  of values to be used for the arguments.

  apply-template will recursively replace argument symbols in expr
  with their corresponding values, returning a modified expr.

  Example: (apply-template '[x] '(+ x x) '[2])
           ;=> (+ 2 2)"
  [argv expr values]
  (assert (vector? argv))
  (assert (every? symbol? argv))
  (walk/postwalk-replace (zipmap argv values) expr))
