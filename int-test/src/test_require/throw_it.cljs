(ns test-require.throw-it)

(println "before throw")
(throw (js/Error. "bye-bye"))
(println "not here")
(def success true)
