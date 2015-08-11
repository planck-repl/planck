(ns test-main.core)

(defn square [x]
  (* x x))

(defn dist [x y]
  (Math/sqrt
    (+ (square x)
       (square y))))

(defn -main [a b]
  (println
    (dist (js/parseInt a)
          (js/parseInt b))))
