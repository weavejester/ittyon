(ns ittyon.core
  (:refer-clojure :exclude [assert time])
  (:require [medley.core :refer [dissoc-in]]))

(def empty-state
  {:snapshot {}
   :index    {:eavt {}, :aevt {}, :avet {}}})

(defn from-snapshot [snapshot]
  {:snapshot snapshot
   :index
   {:eavt (reduce (fn [i [[e a v] t]] (assoc-in i [e a v] t)) {} snapshot)
    :aevt (reduce (fn [i [[e a v] t]] (assoc-in i [a e v] t)) {} snapshot)
    :avet (reduce (fn [i [[e a v] t]] (assoc-in i [a v e] t)) {} snapshot)}})

(defn assert [state [e a v t]]
  (-> state
      (update-in [:snapshot] assoc [e a v] t)
      (update-in [:index :eavt] assoc-in [e a v] t)
      (update-in [:index :aevt] assoc-in [a e v] t)
      (update-in [:index :avet] assoc-in [a v e] t)))

(defn revoke [state [e a v _]]
  (-> state
      (update-in [:snapshot] dissoc [e a v])
      (update-in [:index :eavt] dissoc-in [e a v])
      (update-in [:index :aevt] dissoc-in [a e v])
      (update-in [:index :avet] dissoc-in [a v e])))

(defn event-key [state [o e a v t]] [o a])

(defn time [] (System/currentTimeMillis))

(defn uuid? [x] (instance? java.util.UUID x))

(defn uuid [] (java.util.UUID/randomUUID))

(defmulti validate event-key
  :default ::invalid)

(defmethod validate ::invalid [_ _] false)

(defmethod validate [:assert ::aspect] [_ [o e a v t]]
  (and (uuid? e) (integer? t)))

(defmethod validate [:revoke ::aspect] [_ [o e a v t]]
  (and (uuid? e) (integer? t)))

(defmulti reactions event-key
  :default ::no-op)

(defmethod reactions ::no-op [_ _] '())

(def empty-system
  {:state     empty-state
   :validate  validate
   :reactions reactions})

(def ops #{:assert :revoke})

(defn event? [x]
  (and (sequential? x)
       (= (count x) 5)
       (let [[o e a v t] x]
         (and (ops o) (keyword? a)))))

(defn valid? [system event]
  (and (event? event) ((:validate system) (:state system) event)))

(defn react [system event]
  (let [reactions ((:reactions system) (:state system) event)]
    (concat reactions (mapcat (partial react system) reactions))))

(defn update [system [o e a v t]]
  (let [f (case o :assert assert, :revoke revoke)]
    (update-in system [:state] f [e a v t])))

(defn commit [system event]
  (if (valid? system event)
    (let [events (cons event (seq (react system event)))]
      (reduce update system events))
    system))
