(ns ittyon.core
  (:refer-clojure :exclude [assert]))

(def empty-state
  {:snapshot #{}})

(defn assert [state eavt]
  (update-in state [:snapshot] conj eavt))

(defn retract [state eavt]
  (update-in state [:snapshot] disj eavt))

(defn commit [state [o e a v t]]
  (case o
    :assert  (assert state [e a v t])
    :retract (retract state [e a v t])))
