(ns ittyon.client
  #+clj
  (:require [clojure.core.async :as a :refer [go go-loop <! >!]]
            [ittyon.core :as i])
  #+cljs
  (:require [cljs.core.async :as a :refer [<! >!]]
            [ittyon.core :as i])
  #+cljs
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defmulti receive!
  (fn [client event] (first event)))

(defmethod receive! :default [_ _] nil)

(defmethod receive! :commit [client event]
  (swap! (:state client) #(reduce i/commit % (rest event))))

(defmethod receive! :reset [client event]
  (swap! (:state client) i/reset (second event)))

(defmethod receive! :time [client event]
  (reset! (:time-offset client) (- (i/time) (second event))))

(defn send! [client & revisions]
  (let [time  (+ (i/time) @(:time-offset client))
        revs  (for [r revisions] (conj (vec r) time))
        event (vec (cons :commit revs))]
    (receive! client event)
    (a/put! (:socket client) event)))

(defn tick! [client]
  (swap! (:state client) i/tick (+ (i/time) @(:time-offset client))))

(defn connect!
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
