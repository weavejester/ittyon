(ns ittyon.server
  #+clj
  (:require [clojure.core.async :as a :refer [go go-loop <! >!]]
            [ittyon.core :as i])
  #+cljs
  (:require [cljs.core.async :as a :refer [<! >!]]
            [ittyon.core :as i])
  #+cljs
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn server [init]
  {:system  (atom init)
   :sockets (atom #{})})

(defn broadcast [{:keys [sockets]} socket message]
  (doseq [sock @sockets :when (not= sock socket)]
    (a/put! sock message)))

(defn receive [system event]
  (case (first event)
    :commit (reduce i/commit system (rest event))
    system))

(defn accept [{:keys [sockets system] :as server} socket]
  (go (>! socket [:time (i/time)])
      (>! socket [:reset (-> @system :state :snapshot)])
      (swap! sockets conj socket)
      (loop []
        (when-let [event (<! socket)]
          (swap! system receive event)
          (broadcast server socket event)
          (recur)))
      (swap! sockets disj socket)))
