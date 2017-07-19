(ns test-args.args-in-file
  (:require planck.core))

(println planck.core/*command-line-args*)
(if (exists? *command-line-args*)
  (println ^:cljs.analyzer/no-resolve *command-line-args*)
  (println planck.core/*command-line-args*))
