(ns test-main.unhandled)

(defn call-two []
  (throw (js/Error. "bye")))

(defn call-one []
  (call-two))

(defn -main []
  (call-one))
