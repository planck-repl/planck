(require 'planck.core)
(require 'planck.io)
(binding [planck.core/*err* cljs.core/*out*]
  (pr [['*out* (planck.io/tty? cljs.core/*out*)]
       ['*err* (planck.io/tty? planck.core/*err*)]]))
