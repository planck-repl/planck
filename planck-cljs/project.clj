(defproject planck "0.1.0"
  :profiles {:dev
             {:dependencies [[org.clojure/clojurescript "1.9.229"]
                             [org.clojure/test.check "0.9.0"]
                             [tubular "1.0.0"]]
              :source-paths ["dev"]}
             :build {}}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 ; Comment ClojureScript dep if using a non-official 
                 ; ClojureScript release via planck-cljs/script/build.
                 #_[org.clojure/clojurescript "1.9.229"]
                 [org.clojure/tools.reader "1.0.0-beta3"]
                 [tailrecursion/cljson "1.0.7"]
                 [com.cognitect/transit-clj "0.8.290"]
                 [com.cognitect/transit-cljs "0.8.239"]
                 [fipp "0.6.6"]
                 [malabarba/lazy-map "1.1"]
                 [cljsjs/parinfer "1.8.1-0"]]
  :clean-targets ["out" "target"])
