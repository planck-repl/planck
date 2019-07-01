(ns planck.socket-test
  (:require
   [clojure.string :as string]
   [clojure.test :refer [deftest is testing async]]
   [planck.shell :as shell]
   [planck.socket :as socket]))

(defn darwin? []
  (= "Darwin" (-> (shell/sh "uname") :out string/trim-newline)))

;; Start up an echo server

(def echo-server-port 55555)

(when-not (darwin?)
  (socket/listen echo-server-port
    (fn [socket]
      (fn [socket data]
        (socket/write socket data)))))

(defn latch [m f]
  (let [r (atom 0)]
    (add-watch r :latch
      (fn [_ _ o n]
        (when (== n m) (f))))
    r))

(defn inc! [r]
  (swap! r inc))

#_(deftest listen-protected-port
    (when-not (darwin?)
      (is (thrown-with-msg? js/Error #"Permission denied" (socket/listen 123 (fn [_] (fn [_ _])))))))

#_(deftest integration-test
    (async done
      (let [l (latch 1 done)]
        (let [data-handler (fn [socket data]
                             (is (= "hello" data))
                             (socket/close socket)
                             (inc! l))
              s (socket/connect "localhost" echo-server-port data-handler)]
          (socket/write s "hi")))))
