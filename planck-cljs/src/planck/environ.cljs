(ns planck.environ
  "Facilities for working with environment variables.")

(defonce
  ^{:doc "A map containing environment variables."}
  env (js->clj (js/PLANCK_GETENV)))
