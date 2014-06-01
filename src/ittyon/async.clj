(ns ittyon.async
  (:require [clojure.core.async :as a :refer [go go-loop <! >!]]
            [ittyon.core :as i]))

(defn- sync-system [sysref in]
  (go-loop []
    (when-let [event (<! in)]
      (swap! sysref i/commit event)
      (recur))))

(defn- pipe-input [sysref in out]
  (go-loop []
    (if-let [event (<! in)]
      (let [event (conj event (i/time @sysref))]
        (>! out event)
        (swap! sysref i/commit event)
        (recur))
      (a/close! out))))

(defn connect [sysref socket]
  (let [input (a/chan)]
    (go (let [time     (<! socket)
              offset   (- (i/time) time)
              snapshot (<! socket)]
          (doto sysref
            (swap! assoc :offset offset, :state (i/from-snapshot snapshot))
            (pipe-input input socket)
            (sync-system socket))))
    input))

(defn listen [sysref socket]
  (go (>! socket (i/time))
      (>! socket (-> @sysref :state :snapshot))
      (sync-system sysref socket)))
