(defproject planck "0.1.0"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "0.0-3620"]
                 [org.clojure/tools.reader "0.10.0-SNAPSHOT" :exclusions [org.clojure/clojure]]
                 [org.omcljs/ambly "0.6.0"]]
  :clean-targets ["out" "target"])
