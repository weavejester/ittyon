(ns ittyon.server
  #+cljs
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require #+clj  [clojure.core.async :as a :refer [go go-loop <! >!]]
            #+cljs [cljs.core.async :as a :refer [<! >!]]
            [ittyon.core :as i]
            [medley.core :refer [deref-reset!]]))

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
  (when @sockets
    (go (>! socket [:time (i/time)])
        (>! socket [:reset (-> @system :state :snapshot)])
        (when (swap! sockets #(some-> % (conj socket)))
          (loop []
            (when-let [event (<! socket)]
              (swap! system receive event)
              (broadcast server socket event)
              (recur)))
          (swap! sockets disj socket))
        (a/close! socket))))

(defn shutdown [{:keys [sockets]}]
  (doseq [sock (deref-reset! sockets nil)]
    (a/close! sock)))
