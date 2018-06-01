(ns planck.environ)

(defonce env (js->clj (js/PLANCK_GETENV)))
