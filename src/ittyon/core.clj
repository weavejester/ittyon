(ns ittyon.core
  (:refer-clojure :exclude [assert time]))

(def empty-state
  {:snapshot #{}
   :indexes {:eavt {}, :aevt {}, :avet {}}})

(defn dissoc-in [m [k & ks]]
  (if (seq ks)
    (let [v (dissoc-in (get m k) ks)]
      (if (empty? v)
        (dissoc m k)
        (assoc m k v)))
    (dissoc m k)))

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
