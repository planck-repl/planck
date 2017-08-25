(ns test-main.exit
  (:require planck.core))

(defn call-two []
  (planck.core/exit 17))

(defn call-one []
  (call-two))

(defn my-main []
  (call-one))

(set! *main-cli-fn* my-main)
