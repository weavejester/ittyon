(ns ittyon.async
  (:require [clojure.core.async :as a :refer [go go-loop <! >!]]
            [ittyon.core :as i]))

(defn put-all! [chs msg]
  (doseq [ch chs] (a/put! ch msg)))

(defn close-all! [chs]
  (doseq [ch chs] (a/close! ch)))

(defn connect [sysref socket]
  (let [input (a/chan)]
    (go-loop []
      (let [[event port] (a/alts! [socket input])]
        (if event
          (do (when (identical? port input) (>! socket event))
              (swap! sysref i/recv-client event)
              (recur))
          (close-all! [socket input]))))
    (a/map>
     (fn [[o e a v]] [:commit [o e a v (i/time @sysref)]])
     input)))

(defn listen [sysref sockets socket]
  (let [buffer (a/chan 32)]
    (go (swap! sockets conj buffer)
        (>! socket [:time (i/time)])
        (>! socket [:reset (-> @sysref :state :snapshot)])
        (a/pipe buffer socket)
        (loop []
          (when-let [event (<! socket)]
            (swap! sysref i/recv-server event)
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
