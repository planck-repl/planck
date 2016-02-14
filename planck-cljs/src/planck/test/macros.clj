(ns planck.test.macros
  (:require [planck.test.ana-api :as ana-api]
            [planck.test.template]))

;; =============================================================================
;; Utilities for assertions

(defn function?
  "Returns true if argument is a function or a symbol that resolves to
  a function (not a macro)."
  [menv x]
  (and (symbol? x) (:fn-var (ana-api/resolve menv x))))

(defn assert-predicate
  "Returns generic assertion code for any functional predicate.  The
  'expected' argument to 'report' will contains the original form, the
  'actual' argument will contain the form with all its sub-forms
  evaluated.  If the predicate returns false, the 'actual' form will
  be wrapped in (not...)."
  [msg form]
  (let [args (rest form)
        pred (first form)]
    `(let [values# (list ~@args)
           result# (apply ~pred values#)]
       (if result#
         (cljs.test/do-report
           {:type :pass, :message ~msg,
            :expected '~form, :actual (cons ~pred values#)})
         (cljs.test/do-report
           {:type :fail, :message ~msg,
            :expected '~form, :actual (list '~'not (cons '~pred values#))}))
       result#)))

(defn assert-any
  "Returns generic assertion code for any test, including macros, Java
  method calls, or isolated symbols."
  [msg form]
  `(let [value# ~form]
     (if value#
       (cljs.test/do-report
         {:type :pass, :message ~msg,
          :expected '~form, :actual value#})
       (cljs.test/do-report
         {:type :fail, :message ~msg,
          :expected '~form, :actual value#}))
     value#))

;; =============================================================================
;; Assertion Methods

;; You don't call these, but you can add methods to extend the 'is'
;; macro.  These define different kinds of tests, based on the first
;; symbol in the test expression.

(defmulti assert-expr
  (fn [menv msg form]
    (cond
      (nil? form) :always-fail
      (seq? form) (first form)
      :else :default)))

(defmethod assert-expr :always-fail [menv msg form]
  ;; nil test: always fail
  `(cljs.test/do-report {:type :fail, :message ~msg}))

(defmethod assert-expr :default [menv msg form]
  (if (and (sequential? form)
        (function? menv (first form)))
    (assert-predicate msg form)
    (assert-any msg form)))

(defmethod assert-expr 'instance? [menv msg form]
  ;; Test if x is an instance of y.
  `(let [klass# ~(nth form 1)
         object# ~(nth form 2)]
     (let [result# (instance? klass# object#)]
       (if result#
         (cljs.test/do-report
           {:type :pass, :message ~msg,
            :expected '~form, :actual (type object#)})
         (cljs.test/do-report
           {:type :fail, :message ~msg,
            :expected '~form, :actual (type object#)}))
       result#)))

(defmethod assert-expr 'thrown? [menv msg form]
  ;; (is (thrown? c expr))
  ;; Asserts that evaluating expr throws an exception of class c.
  ;; Returns the exception thrown.
  (let [klass (second form)
        body (nthnext form 2)]
    `(try
       ~@body
       (cljs.test/do-report
         {:type :fail, :message ~msg,
          :expected '~form, :actual nil})
       (catch ~klass e#
         (cljs.test/do-report
           {:type :pass, :message ~msg,
            :expected '~form, :actual e#})
         e#))))

(defmethod assert-expr 'thrown-with-msg? [menv msg form]
  ;; (is (thrown-with-msg? c re expr))
  ;; Asserts that evaluating expr throws an exception of class c.
  ;; Also asserts that the message string of the exception matches
  ;; (with re-find) the regular expression re.
  (let [klass (nth form 1)
        re (nth form 2)
        body (nthnext form 3)]
    `(try
       ~@body
       (cljs.test/do-report {:type :fail, :message ~msg, :expected '~form, :actual nil})
       (catch ~klass e#
         (let [m# (.-message e#)]
           (if (re-find ~re m#)
             (cljs.test/do-report
               {:type :pass, :message ~msg,
                :expected '~form, :actual e#})
             (cljs.test/do-report
               {:type :fail, :message ~msg,
                :expected '~form, :actual e#}))
           e#)))))

(defmacro try-expr
  "Used by the 'is' macro to catch unexpected exceptions.
  You don't call this."
  [msg form]
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
