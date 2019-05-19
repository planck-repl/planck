(ns planck.core
  "Core Planck macros."
  (:refer-clojure :exclude [with-open with-in-str]))

(defmacro with-open
  "bindings => [name IClosable ...]

  Evaluates body in a try expression with names bound to the values of the
  inits, and a finally clause that calls `(-close name)` on each name in reverse
  order."
  [bindings & body]
  ;; when http://dev.clojure.org/jira/browse/CLJS-1551 lands,
  ;; replace with assert-args
  (when-not (vector? bindings)
    (throw (ex-info "with-open requires a vector for its bindings" {})))
  (when-not (even? (count bindings))
    (throw (ex-info "with-open requires an even number of forms in binding vector" {})))
  (cond
    (= (count bindings) 0) `(do ~@body)
    (symbol? (bindings 0)) `(let ~(subvec bindings 0 2)
                              (try
                                (with-open ~(subvec bindings 2) ~@body)
                                (finally
                                  (planck.core/-close ~(bindings 0)))))
    :else (throw (ex-info
                   "with-open only allows symbols in bindings" {}))))

(defmacro with-in-str
  "Evaluates body in a context in which `*in*` is bound to a fresh
  string reader initialized with the string `s`."
  [s & body]
  `(with-open [s# (#'planck.core/make-string-reader ~s)]
     (binding [planck.core/*in* s#]
       ~@body)))
