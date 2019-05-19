(defproject planck "2.23.0"
  :description "Stand-alone ClojureScript REPL"
  :url "https://planck-repl.org"
  :scm {:name "git" :url "https://github.com/planck-repl/planck"}
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["planck-cljs/src"]
  :dependencies [[org.clojure/clojurescript "1.10.520"]
                 [fipp "0.6.14"]
                 [malabarba/lazy-map "1.3"]
                 [com.cognitect/transit-cljs "0.8.248"]
                 [cljsjs/parinfer "1.8.1-0"]])
