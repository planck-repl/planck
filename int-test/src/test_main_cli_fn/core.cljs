(ns test-main-cli-fn.core)

(defn my-main
 [a b]
 (prn a b))

(set! *main-cli-fn* my-main)
