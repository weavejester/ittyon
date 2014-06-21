(ns ittyon.async
  (:refer-clojure :exclude [send])
  (:require [clojure.core.async :as a :refer [go go-loop <! >!]]
            [ittyon.core :as i]))

(defn client [init]
  (let [local  (a/chan)
        remote (atom nil)
        system (atom init)]
    (go-loop []
      (when-let [event (<! local)]
        (some-> @remote (>! event))
        (swap! system i/recv-client event)
        (recur)))
    {:system system
     :local  local
     :remote remote}))

(defn connected? [{:keys [remote]}]
  (not (nil? @remote)))

(defn connect [{:keys [remote system]} socket]
  (reset! remote socket)
  (go-loop []
    (when-let [event (<! socket)]
      (swap! system i/recv-client event)
      (recur))))

(defn send [{:keys [local system]} [o e a v]]
  (a/put! local [:commit [o e a v (i/time @system)]]))

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
