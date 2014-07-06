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
  ([engine event] (receive engine event (i/time)))
  ([engine event local-time]
     (case (first event)
       :commit (reduce i/commit engine (rest event))
       :reset  (assoc engine :state (i/from-snapshot (second event)))
       :time   (assoc engine :time-offset (- local-time (second event)))
       engine)))

(defn connect
  ([socket] (connect socket i/empty-engine))
  ([socket init]
     (let [engine (atom init)
           client (a/chan)]
       (go-loop []
         (when-let [event (<! socket)]
           (swap! engine receive event)
           (when (= (first event) :reset)
             (>! client {:engine engine, :socket socket})
             (a/close! client))
           (recur)))
       client)))

(defn send [{:keys [engine socket]} & revisions]
  (let [time  (i/time @engine)
        revs  (for [r revisions] (conj (vec r) time))
        event (vec (cons :commit revs))]
    (swap! engine receive event)
    (a/put! socket event)))
