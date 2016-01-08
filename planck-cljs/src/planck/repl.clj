(ns planck.repl)

(defmacro doc
  "Prints documentation for a var or special form given its name"
  [sym]
  `(planck.repl/doc* '~sym))

(defmacro source
  "Prints the source code for the given symbol, if it can find it.
  This requires that the symbol resolve to a Var defined in a
  namespace for which the source is available.

  Example: (source filter)"
  [sym]
  `(planck.repl/source* '~sym))

(defmacro pst
  "Prints a stack trace of the exception.

  If none supplied, uses the root cause of the most recent repl exception (*e)"
  ([]
   `(planck.repl/pst*))
  ([e]
   `(planck.repl/pst* '~e)))