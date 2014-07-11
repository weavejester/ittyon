(ns ittyon.core
  (:refer-clojure :exclude [assert time derive])
  #+clj
  (:require [clojure.core :as core]
            [medley.core :refer [dissoc-in]]
            [intentions.core :refer [defintent defconduct]])
  #+cljs
  (:require [cljs.core :as core]
            [medley.core :refer [dissoc-in]]
            [intentions.core :refer-macros [defintent defconduct]]
            [cljs-uuid.core :as uuid]))

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

(defn revision-key [state [o e a v t]] [o a])

(defn time
  ([]
     #+clj  (System/currentTimeMillis)
     #+cljs (.getTime (js/Date.)))
  ([engine]
     (+ (time) (:time-offset engine))))

(defn uuid? [x]
  #+clj  (instance? java.util.UUID x)
  #+cljs (instance? cljs.core.UUID x))

(defn uuid []
  #+clj  (java.util.UUID/randomUUID)
  #+cljs (uuid/make-random))

(defn derive [h? tag & parents]
  (if (map? h?)
    (reduce #(core/derive %1 tag %2) h? parents)
    (doseq [p (cons tag parents)] (core/derive h? p))))

(defintent validate
  :dispatch revision-key
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
  :dispatch revision-key
  :combine concat
  :default ::no-op)

(defconduct reactions ::no-op [_ _] '())

(defconduct reactions [:revoke ::live?] [s [o e a v t]]
  (for [[e avt] (-> s :index :eavt)
        [a vt]  avt
        [v _]   vt
        :when   (not= a ::live?)]
    [:revoke e a v t]))

(defconduct reactions [:assert ::singular] [s [o e a v t]]
  (for [v* (keys (get-in s [:index :eavt e a])) :when (not= v v*)]
    [:revoke e a v* t]))

(defn entity [state id]
  (persistent!
   (reduce-kv
    (fn [m k v]
      (cond
       (isa? k ::live?)    m
       (isa? k ::singular) (assoc! m k (first (keys v)))
       :else               (assoc! m k (set (keys v)))))
    (transient {})
    (get-in state [:index :eavt id]))))

(def empty-engine
  {:state       empty-state
   :time-offset 0
   :validate    validate
   :reactions   reactions})

(defn revision? [x]
  (and (sequential? x)
       (= (count x) 5)
       (let [[o e a v t] x]
         (and (#{:assert :revoke} o) (keyword? a)))))

(defn valid? [engine revision]
  (and (revision? revision) ((:validate engine) (:state engine) revision)))

(defn react [engine revision]
  ((:reactions engine) (:state engine) revision))

(defn update [engine [o e a v t]]
  (let [f (case o :assert assert, :revoke revoke)]
    (update-in engine [:state] f [e a v t])))

(defn commit [engine revision]
  (if (valid? engine revision)
    (let [engine    (update engine revision)
          reactions (react engine revision)]
      (reduce commit engine reactions))
    engine))

(defn tick
  ([engine] (tick engine (time engine)))
  ([engine time]
     (->> (get-in engine [:state :snapshot])
          (mapcat (fn [[[e a _] _]] (react engine [:tick e a time])))
          (reduce commit engine))))

#+clj
(defn periodically [freq func]
  (let [ideal (/ 1000 freq)
        stop? (atom false)]
    (future
      (loop []
        (when-not @stop?
          (let [start (time)]
            (func)
            (let [duration (- (time) start)]
              (Thread/sleep (max 0 (- ideal duration)))
              (recur))))))
    #(reset! stop? true)))

#+cljs
(defn periodically [freq func]
  (let [ideal (/ 1000 freq)
        stop? (atom false)]
    (letfn [(callback []
              (when-not @stop?
                (let [start (time)]
                  (func)
                  (let [duration (- (time) start)]
                    (js/setTimeout callback (max 0 (- ideal duration)))))))]
      (callback)
      #(reset! stop? true))))
