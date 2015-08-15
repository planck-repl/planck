(ns test-main.exit
  (:require planck.core))

(defn call-two []
  (planck.core/exit 17))

(defn call-one []
  (call-two))

(defn -main []
  (call-one))
