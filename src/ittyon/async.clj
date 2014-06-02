(ns ittyon.async
  (:require [clojure.core.async :as a :refer [go go-loop <! >!]]
            [ittyon.core :as i]))

(defn put-all! [chs msg]
  (doseq [ch chs] (a/put! ch msg)))

(defn close-all! [chs]
  (doseq [ch chs] (a/close! ch)))

(defn send-state! [sysref socket]
  (go (>! socket (i/time))
      (>! socket (-> @sysref :state :snapshot))))

(defn recv-state! [sysref socket]
  (go (let [time     (<! socket)
            offset   (- (i/time) time)
            snapshot (<! socket)
            state    (i/from-snapshot snapshot)]
        (swap! sysref assoc :offset offset :state state))))

(defn connect [sysref socket]
  (let [input (a/chan)]
    (go (<! (recv-state! sysref socket))
        (loop []
          (let [[event port] (a/alts! [socket input])]
            (when event
              (when (identical? port input) (>! socket event))
              (swap! sysref i/commit event)
              (recur))))
        (close-all! [socket input]))
    (a/map> #(conj % (i/time @sysref)) input)))

(defn listen [sysref sockets socket]
  (let [buffer (a/chan 32)]
    (go (swap! sockets conj buffer)
        (<! (send-state! sysref socket))
        (a/pipe buffer socket)
        (loop []
          (when-let [event (<! socket)]
            (swap! sysref i/commit event)
            (put-all! (disj @sockets socket) event)
            (recur)))
        (swap! sockets disj buffer)
        (a/close! buffer))))

(defn acceptor [sysref]
  (let [conn    (a/chan)
        sockets (atom #{})]
    (go (loop []
          (when-let [socket (<! conn)]
            (listen sysref sockets socket)
            (recur)))
        (close-all! @sockets))
    conn))
