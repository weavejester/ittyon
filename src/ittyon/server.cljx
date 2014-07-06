(ns ittyon.server
  #+cljs
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require #+clj  [clojure.core.async :as a :refer [go go-loop <! >!]]
            #+cljs [cljs.core.async :as a :refer [<! >!]]
            [ittyon.core :as i]
            [medley.core :refer [deref-reset!]]))

(defn server [init]
  {:engine  (atom init)
   :sockets (atom #{})})

(defn broadcast [{:keys [sockets]} socket message]
  (doseq [sock @sockets :when (not= sock socket)]
    (a/put! sock message)))

(defn receive [engine event]
  (case (first event)
    :commit (reduce i/commit engine (rest event))
    engine))

(defn accept [{:keys [sockets engine] :as server} socket]
  (when @sockets
    (go (>! socket [:time (i/time)])
        (>! socket [:reset (-> @engine :state :snapshot)])
        (when (swap! sockets #(some-> % (conj socket)))
          (loop []
            (when-let [event (<! socket)]
              (swap! engine receive event)
              (broadcast server socket event)
              (recur)))
          (swap! sockets disj socket))
        (a/close! socket))))

(defn shutdown [{:keys [sockets]}]
  (doseq [sock (deref-reset! sockets nil)]
    (a/close! sock)))
