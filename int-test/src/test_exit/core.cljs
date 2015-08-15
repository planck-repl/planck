(ns test-exit.core
  (:require planck.core))

(defn please-exit [exit-value]
  (planck.core/exit exit-value))
