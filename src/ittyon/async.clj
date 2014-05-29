(ns ittyon.async
  (:require [clojure.core.async :as a :refer [go go-loop <! >!]]
            [ittyon.core :as i]))

(defn- send-system [socket system]
  (go (>! socket (i/time))
      (let [time    (<! socket)
            latency (* 2 (- (i/time) time))]
        (>! socket latency)
        (>! socket (-> system :state :snapshot)))))

(defn- recv-system [socket system]
  (go (let [time     (<! socket)
            diff     (- (i/time) time)
            _        (>! socket (+ (i/time) diff))
            latency  (<! socket)
            snapshot (<! socket)]
        (assoc system
          :time-diff (+ diff latency)
          :state     (i/from-snapshot snapshot)))))

(defn- sync-system [socket sysref]
  (go-loop []
    (when-let [event (<! socket)]
      (swap! sysref i/commit event))))

(defn- pipe-input [input socket sysref]
  (go-loop []
    (if-let [event (<! input)]
      (let [diff  (:time-diff @sysref)
            event (conj event (+ (i/time) diff))]
        (>! socket event)
        (swap! sysref i/commit event))
      (a/close! socket))))

(defn connect [socket sysref input]
  (go (let [system (<! (recv-system socket @sysref))]
        (reset! sysref system)
        (pipe-input input socket sysref)
        (sync-system socket sysref))))

(defn listen [socket sysref]
  (go (<! (send-system socket @sysref))
      (sync-system socket sysref)))
