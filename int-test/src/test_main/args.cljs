(ns test-main.args
  [:require [planck.core :as p]])

(defn -main [& args]
  (println "args from main:" args)
  (println "args from *command-line-args*:" p/*command-line-args*)
  0)
