(defproject planck "0.1.0"
  :profiles {:dev
             {:dependencies [[org.clojure/clojurescript "1.9.88"]
                             [tubular "1.0.0"]]
              :source-paths ["dev"]}
             :build {}}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.88"]       ; comment if building own, and revise planck-cljs/script/build
                 [org.clojure/tools.reader "1.0.0-beta1"]
                 [tailrecursion/cljson "1.0.7"]
                 [com.cognitect/transit-clj "0.8.285"]
                 [com.cognitect/transit-cljs "0.8.237"]
                 [fipp "0.6.5"]
                 [malabarba/lazy-map "1.1"]
                 [cljsjs/parinfer "1.8.1-0"]]
  :clean-targets ["out" "target"])
