(ns test-exit.exit-in-file
  (:require planck.core))

(planck.core/exit 111)
(println "don't print this")
(def success true)
