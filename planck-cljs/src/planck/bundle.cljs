(ns planck.bundle
  "Require namespaces so they will be bundled in the Planck binary."
  (:require
   [google-closure-compiler-js]
   [cljs.analyzer.api]
   [cljs.pprint]
   [cljs.spec.alpha]
   [cljs.spec.test.alpha]
   [cljs.test]
   [clojure.core.protocols]
   [clojure.core.reducers]
   [clojure.data]
   [clojure.datafy]
   [clojure.reflect]
   [clojure.zip]
   [fipp.clojure]
   [fipp.deque]
   [fipp.edn]
   [fipp.ednize]
   [fipp.engine]
   [fipp.visit]
   [planck.bundle.gcl]))
