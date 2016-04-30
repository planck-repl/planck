(defproject planck "0.1.0"
  :profiles {:dev
             {:dependencies [[org.clojure/clojurescript "1.8.51"]
                             [tubular "1.0.0"]]}
             :build
             {:dependencies [[com.cognitect/transit-clj "0.8.275"]]}}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.reader "1.0.0-beta1"]
                 [tailrecursion/cljson "1.0.7"]
                 [com.cognitect/transit-cljs "0.8.220"]
                 [malabarba/lazy-map "1.1"]
                 [cljsjs/parinfer "1.5.1-0"]]
  :clean-targets ["out" "target"])
