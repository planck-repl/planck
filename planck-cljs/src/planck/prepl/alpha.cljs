(ns planck.prepl.alpha
  "Planck pREPL implementation.

  Planck's pREPL implementation seeks to provide functionality that is
  equivalent to Clojure's pREPL but in a way that is consistent with Planck's
  REPL architecture. Planck implements its read-eval-print loop as a mixture of
  C and ClojureScript. A C looping function collects input using a reading
  function written in C that is then transmitted to evaluation and printing
  functions that are written in ClojureScript.

  As a result, the functions in this namespace are not intended to be called by
  ClojureScript code. Rather, they are for use by Planck's C code."
  (:refer-clojure :exclude [add-tap remove-tap])
  (:require
    [cljs.repl]
    [cljs.spec.alpha :as s]
    [planck.repl :as repl]
    [planck.socket.alpha :as sock]))

(defn- ^boolean error?
  "Returns true if `t` represents an error or false otherwise.

  Error objects in ClojureScript do not implement a common interface like
  Throwable in Clojure. This function is intended for use with the map that is
  passed to the `out-fn` function in `planck.prepl.alpha/execute`. Sometimes
  the result to be output will be an error and if that is the case, it should be
  converted to edn data by `cljs.repl/Error->map`. The predicate here is taken
  from that function's inplementation."
  [t]
  (or (instance? ExceptionInfo t)
      (instance? js/EvalError t)
      (instance? js/RangeError t)
      (instance? js/ReferenceError t)
      (instance? js/SyntaxError t)
      (instance? js/URIError t)
      (instance? js/Error t)))

(defn- ^:export add-tap
  "Adds `tap` to the environment's set of tap functions.

  The `clojure.core/tap>` function sends its single argument to a set of 'tap'
  functions that are shared between all REPLs within the same environment. The
  architecture of Planck's code is such that the tap function needs to be
  added by the setup logic in the C code. This functions acts as a wrapper that
  can is exported and accessible from C."
  [tap]
  (clojure.core/add-tap tap))

(defn- ^:export remove-tap
  "Removes `tap` from the environment's set of tap functions.

  A function which has been added to a set of 'tap' functions can be removed
  by calling `clojure.code/remove-tap`. Like the `add-tap` function in this
  namespace, `remove-tap` exists to wrap the Clojure function so that it can be
  exported and made accessible from C."
  [tap]
  (clojure.core/remove-tap tap))

(defn ^:export execute
  "Executes `source-text` in namespace `set-ns` with rebound `*print-fn*` and
  `*print-err-fn` functions that use `out-fn`.

  This function acts as a wrapper to `planck.repl/execute`. It creates
  appropriate bindings for ClojureScript's printing functions by binding them to
  an anonymous function that uses `out-fn`. Return values, print statements and
  error messages are all sent as edn forms to `out-fn`.

  The `clojure.core/*1`, `clojure.core/*2`, `clojure.core/*3` and
  `clojure.core/*e` are kept separate from other REPL sessions in the same
  environment using `session-id` as the value under which to store them.

  Finally, this function also sets various options that are passed through the
  `planck.repl/execute` function for use in functions further along the
  execution pipeline.

  The `in-fn` argument is not currently in use."
  [source-text set-ns session-id in-fn out-fn]
  (binding [*print-fn* #(out-fn {:tag :out :val %1})
            *print-err-fn* #(out-fn {:tag :err :val %1})]
    (try
      (let [print-value-fn  #(out-fn {:tag  :ret
                                      :val  (if (error? %1)
                                              (cljs.repl/Error->map %1)
                                              %1)
                                      :ns   (:ns %2)
                                      :ms   (:ms %2)
                                      :form (:form %2)})
            print-err-fn    #(out-fn {:tag       :ret
                                      :val       (cljs.repl/Error->map %1)
                                      :ns        (:ns %2)
                                      :form      (:form %2)
                                      :exception true})
            opts            {:exit?                 false
                             :expression?           true
                             :include-extra-opts?   true
                             :include-stacktrace?   true
                             :print-nil-expression? true
                             :print-err-fn          print-err-fn
                             :print-value-fn        print-value-fn
                             :session-id            session-id
                             :set-ns                set-ns
                             :show-indicator?       false
                             :theme-id              "dumb"
                             :timer?                true}]
        (repl/execute ["text" source-text] opts))
      (catch :default e
        (println e)
        false))))

(defn- ^:export io-channels
  "Returns a JavaScript object containing in, out and tap 'channels' that output
  to a `socket`.

  Clojure's pREPL is designed to allow different implementations of the way data
  is transmitted. Planck's pREPL seeks to achieve a similar effect by allowing
  the in, out and tap channels to be defined independently. These channels are
  ClojureScript functions that can be passed to the `execute` function in this
  namespace or, in the case of the tap channel, added to the set of tap
  functions available in the current environment.

  This function acts as a default implementation for a socket pREPL. The out and
  tap channels use the `planck.socket.alpha/write` function to write to the
  `socket`. This explicit definition is necessary for the tap channel to be
  usable by different threads.

  The in channel is not currently in use."
  [socket]
  (let [out-fn #(binding [*print-fn* (partial sock/write socket)]
                  (let [v (:val %1)]
                    (prn (as-> %1 m
                           (if (#{:ret :tap} (:tag m))
                             (assoc m :val (if (string? v) (identity v) (pr-str v)))
                             (identity m))
                           (into {} (filter (comp some? val) m))))))]
    #js {:in  :stdin
         :out out-fn
         :tap #(out-fn {:tag :tap :val %})}))
