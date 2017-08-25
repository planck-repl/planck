(ns test-main.unhandled)

(defn call-two []
  (throw (js/Error. "bye")))

(defn call-one []
  (call-two))

(defn my-main []
  (call-one))

(set! *main-cli-fn* my-main)
