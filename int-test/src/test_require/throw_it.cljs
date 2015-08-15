(ns test-require.throw-it)

(throw (js/Error. "bye-bye"))
(println "not here")
(def success true)
