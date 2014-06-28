(ns ittyon.client
  (:refer-clojure :exclude [send])
  #+clj
  (:require [clojure.core.async :as a :refer [go go-loop <! >!]]
            [ittyon.core :as i])
  #+cljs
  (:require [cljs.core.async :as a :refer [<! >!]]
            [ittyon.core :as i])
  #+cljs
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn receive
  ([system event] (receive system event (i/time)))
  ([system event local-time]
     (case (first event)
       :commit (reduce i/commit system (rest event))
       :reset  (assoc system :state (i/from-snapshot (second event)))
       :time   (assoc system :offset (- local-time (second event)))
       system)))

(defn connect
  ([socket] (connect socket i/empty-system))
  ([socket init]
     (let [system (atom init)]
       (go-loop []
         (when-let [event (<! socket)]
           (swap! system receive event)
           (recur)))
       {:system system
        :socket socket})))

(defn send [{:keys [system socket]} & revisions]
  (let [time  (i/time @system)
        revs  (for [r revisions] (conj (vec r) time))
        event (vec (cons :commit revs))]
    (swap! system receive event)
    (a/put! socket event)))

