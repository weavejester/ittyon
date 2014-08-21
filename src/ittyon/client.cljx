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
  (swap! (:state client) i/reset (second event)))

(defmethod receive! :time [client event]
  (reset! (:time-offset client) (- (i/time) (second event))))

(defn send!
  "Send one or more messages to the client. A message should be a transition
  with the time element omitted, i.e. [o e a v]."
  [client & messages]
  (let [time  (+ (i/time) @(:time-offset client))
        revs  (for [r revisions] (conj (vec r) time))
        event (vec (cons :commit revs))]
    (receive! client event)
    (a/put! (:socket client) event)))

(defn tick!
  "Move the clock forward on the client."
  [client]
  (swap! (:state client) i/tick (+ (i/time) @(:time-offset client))))

(defn connect!
  "Connect to a server via a bi-directional channel, and return a channel that
  promises to contain the client once the connection has been established. Used
  in conjuction with [[server/accept!]]."
  ([socket] (connect! socket i/empty-state))
  ([socket init-state]
     (let [return (a/chan)
           client {:socket      socket
                   :state       (atom init-state)
                   :time-offset (atom 0)}]
       (go-loop []
         (when-let [event (<! socket)]
           (receive! client event)
           (when (= (first event) :reset)
             (>! return client)
             (a/close! return))
           (recur)))
       return)))
