(defproject planck "0.1.0"
  :profiles {:dev
             {:dependencies [[org.clojure/clojurescript "1.9.521"]
                             [org.clojure/test.check "0.9.0"]
                             [tubular "1.0.0"]]
              :source-paths ["dev"]}
             :build-release 
             {}
             :build-commit
             {}}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.521"]
                 [org.clojure/test.check "0.9.0"]
                 [org.clojure/tools.reader "1.0.0-beta4"]
                 [tailrecursion/cljson "1.0.7"]
                 [com.cognitect/transit-clj "0.8.297"]
                 [com.cognitect/transit-cljs "0.8.239"]
                 [fipp "0.6.7"]
                 [malabarba/lazy-map "1.3"]
                 [cljsjs/parinfer "1.8.1-0"]]
  :clean-targets ["out" "target"])
