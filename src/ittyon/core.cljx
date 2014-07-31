(ns ittyon.core
  (:refer-clojure :exclude [assert time derive find])
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

(defconduct validate [:assert ::ref] [s [o e a v t]]
  (get-in s [:index :eavt v]))

(defconduct validate [:revoke ::live?] [_ [o e a v t]]
  (integer? t))

(defconduct validate [:revoke ::aspect] [_ [o e a v t]]
  (integer? t))

(defintent reactions
  :dispatch revision-key
  :combine concat
  :default ::no-op)

(defconduct reactions ::no-op [_ _] '())

(defn- revoke-aspects [s e t]
  (for [[a vt] (get-in s [:index :eavt e])
        [v _]  vt
        :when  (not= a ::live?)]
    [:revoke e a v t]))

(defn- revoke-refs [s v t]
  (for [a     (cons ::ref (descendants ::ref))
        [e _] (get-in s [:index :avet a v])
        :when e]
    [:revoke e a v t]))

(defconduct reactions [:revoke ::live?] [s [o e a v t]]
  (concat (revoke-aspects s e t)
          (revoke-refs s e t)))

(defconduct reactions [:assert ::singular] [s [o e a v t]]
  (for [v* (keys (get-in s [:index :eavt e a])) :when (not= v v*)]
    [:revoke e a v* t]))

(defn- find-with [f s e a d]
  (let [value (get-in s [:index :eavt e a] ::not-found)]
    (if (= value ::not-found)
      d
      (f value))))

(defn find
  ([s e a] (find s e a nil))
  ([s e a d]
     (cond
      (= a ::id)          e
      (isa? a ::singular) (find-with (comp first keys) s e a d)
      :else               (find-with #(mapv key (sort-by val %)) s e a d))))

(defn entity [state id]
  (let [getter (memoize #(find state id %1 %2))]
    #+clj  (reify
             clojure.lang.IFn
             (invoke [_ aspect] (getter aspect nil))
             (invoke [_ aspect not-found] (getter aspect not-found))
             clojure.lang.ILookup
             (valAt [_ aspect] (getter aspect nil))
             (valAt [_ aspect not-found] (getter aspect not-found)))
    #+cljs (reify
             IFn
             (-invoke [_ aspect] (getter aspect nil))
             (-invoke [_ aspect not-found] (getter aspect not-found))
             ILookup
             (-lookup [_ aspect] (getter aspect nil))
             (-lookup [_ aspect not-found] (getter aspect not-found)))))

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
