(ns ittyon.client
  "A client that communicates with a server to mirror its state."
  #+cljs
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require #+clj  [clojure.core.async :as a :refer [go go-loop <! >!]]
            #+cljs [cljs.core.async :as a :refer [<! >!]]
            [ittyon.core :as i]))

(defmulti ^:no-doc receive!
  (fn [client event] (first event)))

(defmethod receive! :default [_ _] nil)

(defmethod receive! :commit [client event]
  (swap! (:state client) #(reduce i/commit % (rest event))))

(defmethod receive! :reset [client event]
  (reset! (:state client) (i/state (second event))))

(defmethod receive! :time [client event]
  (reset! (:time-offset client) (- (i/time) (second event))))

(defn send!
  "Send one or more messages to the client. A message should be a transition
  with the time element omitted, i.e. `[o e a v]`."
  [client & messages]
  (let [time  (+ (i/time) @(:time-offset client))
        msgs  (for [m messages] (conj (vec m) time))
        event (vec (cons :commit msgs))]
    (receive! client event)
    (a/put! (:socket client) event)))

(defn tick!
  "Move the clock forward on the client."
  [client]
  (swap! (:state client) i/tick (+ (i/time) @(:time-offset client))))

(defn- handshake! [socket]
  (letfn [(expect [t [e d]] {:pre [(= e t)]} d)]
    (go (let [identity (expect :identity (<! socket))
              time     (expect :time     (<! socket))
              reset    (expect :reset    (<! socket))]
          {:socket      socket
           :identity    identity
           :state       (atom (i/state reset))
           :time-offset (atom (- (i/time) time))}))))

(defn connect!
  "Connect to a server via a bi-directional channel, and return a channel that
  promises to contain the client once the connection has been established. Used
  in conjuction with [[server/accept!]]."
  [socket]
  (let [return  (a/chan)
        clientp (handshake! socket)]
    (go (let [client (<! clientp)]
          (>! return client)
          (a/close! clientp)
          (a/close! return)
          (loop []
            (when-let [event (<! socket)]
              (receive! client event)
              (recur)))))
    return))
