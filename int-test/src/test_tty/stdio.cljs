(require 'planck.core)
(require 'planck.io)
(pr [['*in*  (planck.io/tty? planck.core/*in*)]
     ['*out* (planck.io/tty? cljs.core/*out*)]
     ['*err* (planck.io/tty? planck.core/*err*)]])
