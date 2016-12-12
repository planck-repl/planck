(ns planck.setup-eval
  (:require-macros [cljs.spec.test])
  (:require [planck.core :refer [eval intern]]))

;; The cljs.spec.test macros namespace makes use of eval.
;; Intern Planck's implementation of eval for use there.
(intern 'cljs.spec.test$macros 'eval eval)