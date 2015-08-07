(ns test-require-macros.core)

(defmacro str->int [s]
  #?(:clj  (Integer/parseInt s)
     :cljs (js/parseInt s)))
