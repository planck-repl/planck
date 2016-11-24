(ns planck.setup-eval
  (:require-macros [cljs.spec.test])
  (:require [planck.core :refer [eval]]))

;; The cljs.spec.test macros namespace makes use of eval.
;; Intern Planck's implementation of eval for use there.
(planck.core/intern 'cljs.spec.test$macros 'eval eval)