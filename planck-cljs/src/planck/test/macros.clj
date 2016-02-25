(ns planck.test.macros
  (:require [planck.test.ana-api :as ana-api]
            [planck.test.template]))

(defmacro try-expr
  "Used by the 'is' macro to catch unexpected exceptions.
  You don't call this."
  [assert-expr msg form]
  `(try
     ~(assert-expr &env msg form)
     (catch :default t#
       (cljs.test/do-report
         {:type :error, :message ~msg,
          :expected '~form, :actual t#}))))

(defmacro do-template
  "Repeatedly copies expr (in a do block) for each group of arguments
  in values.  values are automatically partitioned by the number of
  arguments in argv, an argument vector as in defn.

  Example: (macroexpand '(do-template [x y] (+ y x) 2 4 3 5))
           ;=> (do (+ 4 2) (+ 5 3))"
  [argv expr & values]
  (let [c (count argv)]
    `(do ~@(map (fn [a] (planck.test.template/apply-template argv expr a))
             (partition c values)))))
