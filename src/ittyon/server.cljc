(ns ittyon.server
  "A server that keeps the state of its clients in sync with one another."
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]]))
  (:require #?(:clj  [clojure.core.async :as a :refer [go go-loop <! >!]]
               :cljs [cljs.core.async :as a :refer [<! >!]])
            [ittyon.core :as i]
            [ittyon.client :as ic]
            [medley.core :refer [deref-reset!]]))

(defn server
  "Create a new server with the supplied initial state."
  [init-state]
  {:state      (atom init-state)
   :sockets    (atom #{})
   :ping-delay 10000
   :logger     println})

(defn shutdown!
  "Shutdown the supplied server and atomically close all open sockets."
  [server]
  (doseq [sock (deref-reset! (:sockets server) nil)]
    (a/close! sock)))

(defn ^:no-doc broadcast! [server socket message]
  (doseq [sock @(:sockets server) :when (not= sock socket)]
    (a/put! sock message)))

(defn- transact! [server transitions]
  (ic/log-exceptions server #(swap! (:state server) i/transact transitions)))

(defmulti ^:no-doc receive!
  (fn [server socket event] (first event)))

(defmethod receive! :default [_ _ _] nil)

(defmethod receive! :transact [server socket [_ transitions]]
  (when-let [state (transact! server transitions)]
    (let [impure (filter (comp :impure meta) (reverse (:log state)))]
      (when (seq impure)
        (a/put! socket [:transact (vec impure)]))
      (when-let [trans (seq (concat transitions impure))]
        (broadcast! server socket [:transact (vec trans)])))))

(defn tick!
  "Move the clock forward on the server."
  [server]
  (swap! (:state server) i/tick (i/time)))

(defn- connect-event [client-id]
  [:transact [[:assert client-id ::i/live? true (i/time)]
              [:assert client-id ::ic/connected? true (i/time)]]])

(defn- disconnect-event [client-id]
  [:transact [[:revoke client-id ::i/live? true (i/time)]]])

(defn- local-fact? [[_ a _ _]]
  (isa? a ::local))

(defn- handshake-event [client-id init-state]
  [:init {:id    client-id
          :time  (i/time)
          :reset (remove local-fact? (i/facts init-state))}])

(defn accept!
  "Accept a new connection in the form of a bi-directional core.async channel.
  Used in conjuction with [[client/connect!]]."
  [{:keys [sockets state ping-delay] :as server} socket]
  (when (swap! sockets #(some-> % (conj socket)))
    (let [client-id (i/uuid)]
      (go (receive! server socket (connect-event client-id))
          (>! socket (handshake-event client-id @state))
          (loop [timer (a/timeout ping-delay)]
            (let [[val port] (a/alts! [socket timer])]
              (if (identical? port socket)
                (when val
                  (receive! server socket val)
                  (recur timer))
                (do
                  (>! socket [:time (i/time)])
                  (recur (a/timeout ping-delay))))))
          (receive! server socket (disconnect-event client-id))
          (swap! sockets disj socket)
          (a/close! socket)))))
