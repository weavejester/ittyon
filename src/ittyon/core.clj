(ns ittyon.core
  (:refer-clojure :exclude [assert time])
  (:require [medley.core :refer [dissoc-in]]))

(def empty-state
  {:snapshot #{}
   :indexes {:eavt {}, :aevt {}, :avet {}}})

(defn time []
  (System/currentTimeMillis))

(defn assert [state [e a v t]]
  (-> state
      (update-in [:snapshot] conj [e a v t])
      (assoc-in [:indexes :eavt e a v t] true)
      (assoc-in [:indexes :aevt a e v t] true)
      (assoc-in [:indexes :avet a v e t] true)))

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
