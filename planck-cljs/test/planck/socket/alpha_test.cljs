(ns planck.socket.alpha-test
  (:require
   [clojure.test :refer [deftest is testing async]]
   [planck.socket.alpha :as socket]))

;; Start up an echo server

(def echo-server-port 55555)

(socket/listen echo-server-port
  (fn [socket]
    (fn [socket data]
      (socket/write socket data))))

(defn latch [m f]
  (let [r (atom 0)]
    (add-watch r :latch
      (fn [_ _ o n]
        (when (== n m) (f))))
    r))

(defn inc! [r]
  (swap! r inc))

(deftest listen-protected-port
  (is (thrown-with-msg? js/Error #"Permission denied" (socket/listen 123 (fn [_] (fn [_ _]))))))

#_(deftest integration-test
  (async done
    (let [l (latch 1 done)]
      (let [data-handler (fn [socket data]
                           (is (= "hello" data))
                           (socket/close socket)
                           (inc! l))
            s (socket/connect "localhost" echo-server-port data-handler)]
        (socket/write s "hi")))))
