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

(defn connect
  ([socket] (connect socket i/empty-system))
  ([socket init]
     (let [system (atom init)]
       (go-loop []
         (when-let [event (<! socket)]
           (swap! system i/recv-client event)
           (recur)))
       {:system system
        :socket socket})))

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

(defn shutdown [{:keys [sockets]}]
  (doseq [s @sockets]
    (a/close! s)))
