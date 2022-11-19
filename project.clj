(defproject planck "2.27.0"
  :description "Stand-alone ClojureScript REPL"
  :url "https://planck-repl.org"
  :scm {:name "git" :url "https://github.com/planck-repl/planck"}
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["planck-cljs/src"]
  :dependencies [[org.clojure/clojurescript "1.11.60"]
                 [org.clojure/core.rrb-vector "0.1.2"]
                 [fipp/fipp "0.6.24"]
                 [malabarba/lazy-map "1.3"]
                 [com.cognitect/transit-cljs "0.8.269"]
                 [com.cognitect/transit-js "0.8.861"]])
