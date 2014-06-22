(ns ittyon.async
  (:refer-clojure :exclude [send])
  (:require [clojure.core.async :as a :refer [go go-loop <! >!]]
            [ittyon.core :as i]))

(defn client [init]
  {:system (atom init)
   :socket (atom nil)})

(defn connected? [{:keys [socket]}]
  (not (nil? @socket)))

(defn connect [{:keys [system] :as client} socket]
  (reset! (:socket client) socket)
  (go-loop []
    (when-let [event (<! socket)]
      (swap! system i/recv-client event)
      (recur))))

(defn send [{:keys [system socket]} [o e a v]]
  (let [event [:commit [o e a v (i/time @system)]]]
    (swap! system i/recv-client event)
    (a/put! @socket event)))

(defn server [init]
  {:system  (atom init)
   :sockets (atom #{})})

(defn broadcast [{:keys [sockets]} socket message]
  (doseq [sock @sockets :when (not= sock socket)]
    (a/put! sock message)))

(defn accept [{:keys [sockets system] :as server} socket]
  (go (>! socket [:time (i/time)])
      (>! socket [:reset (-> @system :state :snapshot)])
      (swap! sockets conj socket)
      (loop []
        (when-let [event (<! socket)]
          (swap! system i/recv-server event)
          (broadcast server socket event)
          (recur)))
      (swap! sockets disj socket)))

(defn shutdown [{:keys [sockets]}]
  (doseq [s @sockets]
    (a/close! s)))
