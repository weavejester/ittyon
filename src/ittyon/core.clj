(ns ittyon.core
  (:refer-clojure :exclude [assert time])
  (:require [medley.core :refer [dissoc-in]]
            [intentions.core :refer [defintent defconduct]]))

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

(defn time
  ([] (System/currentTimeMillis))
  ([system] (+ (time) (:offset system))))

(defn uuid? [x] (instance? java.util.UUID x))

(defn uuid [] (java.util.UUID/randomUUID))

(defintent validate
  :dispatch event-key
  :combine #(and %1 %2)
  :default ::invalid)

(defconduct validate ::invalid [_ _] false)

(defconduct validate [:assert ::live?] [_ [o e a v t]]
  (and (uuid? e) (integer? t) (true? v)))

(defconduct validate [:assert ::aspect] [s [o e a v t]]
  (and (uuid? e) (integer? t) (get-in s [:index :eavt e ::live?])))

(defconduct validate [:revoke ::live?] [_ [o e a v t]]
  (integer? t))

(defconduct validate [:revoke ::aspect] [_ [o e a v t]]
  (integer? t))

(defintent reactions
  :dispatch event-key
  :combine concat
  :default ::no-op)

(defconduct reactions ::no-op [_ _] '())

(defconduct reactions [:revoke ::live?] [s [o e a v t]]
  (for [[e avt] (-> s :index :eavt)
        [a vt]  avt
        [v _]   vt]
    [:revoke e a v t]))

(def empty-system
  {:state     empty-state
   :offset    0
   :validate  validate
   :reactions reactions})

(defn event? [x]
  (and (sequential? x)
       (= (count x) 5)
       (let [[o e a v t] x]
         (and (#{:assert :revoke} o) (keyword? a)))))

(defn valid? [system event]
  (and (event? event) ((:validate system) (:state system) event)))

(defn react [system event]
  ((:reactions system) (:state system) event))

(defn update [system [o e a v t]]
  (let [f (case o :assert assert, :revoke revoke)]
    (update-in system [:state] f [e a v t])))

(defn commit [system event]
  (if (valid? system event)
    (let [system    (update system event)
          reactions (react system event)]
      (reduce commit system reactions))
    system))

(defn tick
  ([system] (tick system (time system)))
  ([system time]
     (->> (get-in system [:state :snapshot])
          (mapcat (fn [[[e a _] _]] (react system [:tick e a time])))
          (reduce commit system))))
