(ns ittyon.async
  (:refer-clojure :exclude [send])
  #+clj
  (:require [clojure.core.async :as a :refer [go go-loop <! >!]]
            [ittyon.core :as i])
  #+cljs
  (:require [cljs.core.async :as a :refer [<! >!]]
            [ittyon.core :as i])
  #+cljs
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn client [init]
  {:system (atom init)
   :socket nil})

(defn connected? [client]
  (not (nil? (:socket client))))

(defn connect [{:keys [system] :as client} socket]
  (go-loop []
    (when-let [event (<! socket)]
      (swap! system i/recv-client event)
      (recur)))
  (assoc client :socket socket))

(defn send [{:keys [system socket]} & revisions]
  (let [time  (i/time @system)
        revs  (for [r revisions] (conj (vec r) time))
        event (vec (cons :commit revs))]
    (swap! system i/recv-client event)
    (a/put! socket event)))

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

(defn ticker [system rate]
  (let [ideal (/ 1000 rate)
        valve (a/chan)]
    (go-loop []
      (let [start-time (i/time)]
        (swap! system i/tick)
        (let [duration  (- (i/time) start-time)
              wait-time (max 0 (- ideal duration))
              [_ port]  (a/alts! [valve (a/timeout wait-time)])]
          (when-not (identical? port valve)
            (recur)))))
    valve))

(defn add-ticker [client-or-server rate]
  (let [system (:system client-or-server)]
    (assoc client-or-server :ticker (ticker system rate))))

(defn shutdown [{:keys [sockets socket ticker]}]
  (when ticker
    (a/close! ticker))
  (when socket
    (a/close! socket))
  (when sockets
    (doseq [s @sockets]
      (a/close! s))))
