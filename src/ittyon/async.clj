(ns ittyon.async
  (:require [clojure.core.async :as a :refer [go go-loop <! >!]]
            [ittyon.core :as i]))

(defn- sync-system [socket sysref]
  (go-loop []
    (when-let [event (<! socket)]
      (swap! sysref i/commit event)
      (recur))))

(defn- pipe-input [input socket sysref]
  (go-loop []
    (if-let [event (<! input)]
      (let [event (conj event (i/time @sysref))]
        (>! socket event)
        (swap! sysref i/commit event)
        (recur))
      (a/close! socket))))

(defn connect [socket sysref input]
  (go (let [time     (<! socket)
            offset   (- (i/time) time)
            snapshot (<! socket)]
        (swap! sysref assoc :offset offset, :state (i/from-snapshot snapshot))
        (pipe-input input socket sysref)
        (sync-system socket sysref))))

(defn listen [socket sysref]
  (go (>! socket (i/time))
      (>! socket (-> @sysref :state :snapshot))
      (sync-system socket sysref)))
