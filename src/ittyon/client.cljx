(ns ittyon.client
  "A client that communicates with a server to mirror its state."
  #+cljs
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require #+clj  [clojure.core.async :as a :refer [go go-loop <! >!]]
            #+cljs [cljs.core.async :as a :refer [<! >!]]
            #+clj  [intentions.core :refer [defconduct]]
            #+cljs [intentions.core :refer-macros [defconduct]]
            [ittyon.core :as i]
            [medley.core :refer [boolean?]]))

(derive ::connected? ::i/aspect)

(defconduct i/-valid? [:assert ::connected?] [_ [_ _ _ v _]]
  (boolean? v))

(defmulti ^:no-doc receive!
  (fn [client event] (first event)))

(defmethod receive! :default [_ _] nil)

(defmethod receive! :commit [client event]
  (i/transact! (:state client) (rest event)))

(defmethod receive! :reset [client event]
  (reset! (:state client) (i/state (second event))))

(defmethod receive! :time [client event]
  (reset! (:time-offset client) (- (i/time) (second event))))

(defn- local? [[_ _ a _]]
  (isa? a ::local))

(defn send!
  "Send one or more messages to the client. A message should be a transition
  with the time element omitted, i.e. `[o e a v]`. Aspects deriving from
  `:ittyon.client/local` are not relayed to the server."
  [client & messages]
  (let [time (+ (i/time) @(:time-offset client))
        msgs (for [m messages] (conj (vec m) time))]
    (receive! client `[:commit ~@msgs])
    (a/put! (:socket client) `[:commit ~@(remove local? msgs)])))

(defn tick!
  "Move the clock forward on the client."
  [client]
  (swap! (:state client) i/tick (+ (i/time) @(:time-offset client))))

(defn- make-client
  [socket [event-type {:keys [id time reset]}]]
  {:pre [(= event-type :init)]}
  {:socket      socket
   :id          id
   :state       (atom (i/state reset))
   :time-offset (atom (- (i/time) time))})

(defn connect!
  "Connect to a server via a bi-directional channel, and return a channel that
  promises to contain the client once the connection has been established. Used
  in conjuction with [[server/accept!]]."
  [socket]
  (let [return (a/chan)]
    (go (let [client (make-client socket (<! socket))]
          (>! return client)
          (a/close! return)
          (loop []
            (when-let [event (<! socket)]
              (receive! client event)
              (recur)))))
    return))
