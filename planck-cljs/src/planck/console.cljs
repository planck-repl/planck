(ns ^:no-doc planck.console
  "Implementation of js/console"
  (:refer-clojure :exclude [time]))

;; Base logging functions

(defn- log-stdout [& args]
  (apply js/PLANCK_CONSOLE_STDOUT args))

(defn- log-stderr [& args]
  (apply js/PLANCK_CONSOLE_STDERR args))

;; Timing functions
;; See https://console.spec.whatwg.org/#timing

(def ^:private timer-state (atom {}))

(defn- add-label [state label start-time]
  (let [label-exists? (contains? (:timer-table state) label)]
    (cond-> state
      true (assoc :label-exists? label-exists?)
      (not label-exists?) (assoc-in [:timer-table label] start-time))))

(defn- time [label]
  (let [new-state (swap! timer-state add-label label (system-time))]
    (when (:label-exists? new-state)
      (log-stderr "label" label "already exists"))))

(defn- timer-prefix [label start-time]
  (str label ": " (- (system-time) start-time) " ms"))

(defn- maybe-log-timing [label start-time & data]
  (if (some? start-time)
    (apply log-stdout (timer-prefix label start-time) data)
    (log-stderr "label" label "does not exist")))

(defn- time-log [label & data]
  (apply maybe-log-timing label (get-in @timer-state [:timer-table label]) data))

(defn- remove-label [state label]
  (-> state
    (assoc :start-time (get-in state [:timer-table label]))
    (update :timer-table dissoc label)))

(defn- time-end [label & data]
  (let [new-state (swap! timer-state remove-label label)]
    (apply maybe-log-timing label (:start-time new-state) data)))

;; The global js/console object

(set! js/console
  #js {:log     log-stdout
       :trace   log-stdout
       :debug   log-stdout
       :info    log-stdout
       :warn    log-stderr
       :error   log-stderr
       :time    time
       :timeLog time-log
       :timeEnd time-end})
