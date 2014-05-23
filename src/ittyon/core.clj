(ns ittyon.core
  (:refer-clojure :exclude [assert time])
  (:require [medley.core :refer [dissoc-in]]))

(def empty-state
  {:snapshot {}
   :indexes  {:eavt {}, :aevt {}, :avet {}}})

(defn from-snapshot [snapshot]
  {:snapshot snapshot
   :indexes
   {:eavt (reduce (fn [i [[e a v] t]] (assoc-in i [e a v] t)) {} snapshot)
    :aevt (reduce (fn [i [[e a v] t]] (assoc-in i [a e v] t)) {} snapshot)
    :avet (reduce (fn [i [[e a v] t]] (assoc-in i [a v e] t)) {} snapshot)}})

(defn time []
  (System/currentTimeMillis))

(defn assert [state [e a v t]]
  (-> state
      (update-in [:snapshot] assoc [e a v] t)
      (update-in [:indexes :eavt] assoc-in [e a v] t)
      (update-in [:indexes :aevt] assoc-in [a e v] t)
      (update-in [:indexes :avet] assoc-in [a v e] t)))

(defn revoke [state [e a v _]]
  (-> state
      (update-in [:snapshot] dissoc [e a v])
      (update-in [:indexes :eavt] dissoc-in [e a v])
      (update-in [:indexes :aevt] dissoc-in [a e v])
      (update-in [:indexes :avet] dissoc-in [a v e])))

(defn event-key [state [o e a v t]] [o a])

(defmulti validate event-key
  :default ::invalid)

(defmethod validate ::invalid [_ _] false)

(defmethod validate [:assert ::aspect] [_ _] true)
(defmethod validate [:revoke ::aspect] [_ _] true)

(defmulti reactions event-key
  :default ::no-op)

(defmethod reactions ::no-op [_ _] '())

(def empty-system
  {:state     empty-state
   :validate  validate
   :reactions reactions})

(defn valid? [system event]
  (boolean ((:validate system) (:state system) event)))

(defn react [system event]
  (let [reactions ((:reactions system) (:state system) event)]
    (mapcat (partial react system) reactions)))

(defn update [system [o e a v t]]
  (let [f (case o :assert assert, :revoke revoke)]
    (update-in system [:state] f [e a v t])))

(defn commit [system event]
  (if (valid? system event)
    (let [events (cons event (seq (react system event)))]
      (reduce update system events))
    system))
