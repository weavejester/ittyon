(ns ittyon.core
  (:refer-clojure :exclude [assert time])
  (:require [medley.core :refer [dissoc-in]]))

(def empty-state
  {:snapshot #{}
   :indexes  {:eavt {}, :aevt {}, :avet {}}})

(defn- add-eavt [i [e a v t]] (assoc-in i [e a v t] true))
(defn- add-aevt [i [e a v t]] (assoc-in i [a e v t] true))
(defn- add-avet [i [e a v t]] (assoc-in i [a v e t] true))

(defn from-snapshot [snapshot]
  {:snapshot (set snapshot)
   :indexes  {:eavt (reduce add-eavt {} snapshot)
              :aevt (reduce add-aevt {} snapshot)
              :avet (reduce add-avet {} snapshot)}})

(defn time []
  (System/currentTimeMillis))

(defn assert [state eavt]
  (-> state
      (update-in [:snapshot] conj eavt)
      (update-in [:indexes :eavt] add-eavt eavt)
      (update-in [:indexes :aevt] add-aevt eavt)
      (update-in [:indexes :avet] add-avet eavt)))

(defn retract [state [e a v t]]
  (-> state
      (update-in [:snapshot] disj [e a v t])
      (update-in [:indexes :eavt] dissoc-in [e a v t])
      (update-in [:indexes :aevt] dissoc-in [a e v t])
      (update-in [:indexes :avet] dissoc-in [a v e t])))

(defn commit [state [o e a v t]]
  (case o
    :assert  (assert state [e a v t])
    :retract (retract state [e a v t])))
