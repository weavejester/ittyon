(ns ittyon.server
  #+cljs
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require #+clj  [clojure.core.async :as a :refer [go go-loop <! >!]]
            #+cljs [cljs.core.async :as a :refer [<! >!]]
            [ittyon.core :as i]
            [medley.core :refer [deref-reset!]]))

(defn server [init-state]
  {:state   (atom init-state)
   :sockets (atom #{})})

(defn shutdown! [{:keys [sockets]}]
  (doseq [sock (deref-reset! sockets nil)]
    (a/close! sock)))

(defn broadcast! [server socket message]
  (doseq [sock @(:sockets server) :when (not= sock socket)]
    (a/put! sock message)))

(defmulti receive!
  (fn [server event] (first event)))

(defmethod receive! :default [_ _] nil)

(defmethod receive! :commit [server event]
  (swap! (:state server) #(reduce i/commit % (rest event))))

(defn accept! [{:keys [sockets state] :as server} socket]
  (when @sockets
    (go (>! socket [:time  (i/time)])
        (>! socket [:reset (i/facts @state)])
        (when (swap! sockets #(some-> % (conj socket)))
          (loop []
            (when-let [event (<! socket)]
              (receive! server event)
              (broadcast! server socket event)
              (recur)))
          (swap! sockets disj socket))
        (a/close! socket))))
