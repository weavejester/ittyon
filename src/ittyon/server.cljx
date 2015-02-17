(ns ittyon.server
  "A server that keeps the state of its clients in sync with one another."
  #+cljs
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require #+clj  [clojure.core.async :as a :refer [go go-loop <! >!]]
            #+cljs [cljs.core.async :as a :refer [<! >!]]
            [ittyon.core :as i]
            [medley.core :refer [deref-reset!]]))

(defn server
  "Create a new server with the supplied initial state."
  [init-state]
  {:state   (atom init-state)
   :sockets (atom #{})})

(defn shutdown!
  "Shutdown the supplied server and atomically close all open sockets."
  [server]
  (doseq [sock (deref-reset! (:sockets server) nil)]
    (a/close! sock)))

(defn ^:no-doc broadcast! [server socket message]
  (doseq [sock @(:sockets server) :when (not= sock socket)]
    (a/put! sock message)))

(defmulti ^:no-doc receive!
  (fn [server event] (first event)))

(defmethod receive! :default [_ _] nil)

(defmethod receive! :commit [server event]
  (swap! (:state server) #(reduce i/commit % (rest event))))

(defn tick!
  "Move the clock forward on the server."
  [server]
  (swap! (:state server) i/tick (i/time)))

(defn- connect-event [client-id]
  [:commit [:assert client-id ::i/live? true (i/time)]])

(defn- disconnect-event [client-id]
  [:commit [:revoke client-id ::i/live? true (i/time)]])

(defn- handshake-event [client-id init-state]
  [:init {:identity client-id
          :time     (i/time)
          :reset    (i/facts init-state)}])

(defn- make-handler [server socket]
  (fn [event]
    (receive! server event)
    (broadcast! server socket event)))

(defn accept!
  "Accept a new connection in the form of a bi-directional core.async channel.
  Used in conjuction with [[client/connect!]]."
  [{:keys [sockets state] :as server} socket]
  (when (swap! sockets #(some-> % (conj socket)))
    (let [handle!   (make-handler server socket)
          client-id (i/uuid)]
      (go (handle! (connect-event client-id))
          (>! socket (handshake-event client-id @state))
          (loop []
            (when-let [event (<! socket)]
              (handle! event)
              (recur)))
          (handle! (disconnect-event client-id))
          (swap! sockets disj socket)
          (a/close! socket)))))
