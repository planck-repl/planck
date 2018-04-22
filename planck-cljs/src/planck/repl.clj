(ns planck.repl
  "Macros for use at the Planck REPL.")

(defmacro apropos
  "Given a regular expression or stringable thing, return a seq of all public
  definitions in all currently-loaded namespaces that match the str-or-pattern."
  [str-or-pattern]
  `(^:private-var-access-nowarn planck.repl/apropos* '~str-or-pattern))

(defmacro dir
  "Prints a sorted directory of public vars in a namespace"
  [nsname]
  `(^:private-var-access-nowarn planck.repl/dir* '~nsname))

(defmacro find-doc
  "Prints documentation for any var whose documentation or name contains a
  match for re-string-or-pattern"
  [re-string-or-pattern]
  `(^:private-var-access-nowarn planck.repl/find-doc* '~re-string-or-pattern))

(defmacro doc
  "Prints documentation for a var or special form given its name"
  [sym]
  `(^:private-var-access-nowarn planck.repl/doc* '~sym))

(defmacro source
  "Prints the source code for the given symbol, if it can find it. This
  requires that the symbol resolve to a Var defined in a namespace for which
  the source is available.

  Example: (source filter)"
  [sym]
  `(^:private-var-access-nowarn planck.repl/source* '~sym))

(defmacro pst
  "Prints a stack trace of the exception.

  If none supplied, uses the root cause of the most recent repl exception (*e)"
  ([]
   `(^:private-var-access-nowarn planck.repl/pst*))
  ([e]
   `(^:private-var-access-nowarn planck.repl/pst* '~e)))

(defmacro ^:private with-err-str
  "Evaluates exprs in a context in which *print-err-fn* is bound to .append on
  a fresh StringBuffer. Returns the string created by any nested printing
  calls."
  [& body]
  `(let [sb# (js/goog.string.StringBuffer.)]
     (binding [cljs.core/*print-newline* true
               cljs.core/*print-err-fn*  (fn [x#] (.append sb# x#))]
       ~@body)
     (str sb#)))
