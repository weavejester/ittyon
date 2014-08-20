(ns ittyon.core
  (:refer-clojure :exclude [assert time derive find])
  #+clj
  (:require [clojure.core :as core]
            [medley.core :refer [dissoc-in map-keys]]
            [intentions.core :refer [defintent defconduct]])
  #+cljs
  (:require [cljs.core :as core]
            [medley.core :refer [dissoc-in map-keys]]
            [intentions.core :refer-macros [defintent defconduct]]
            [cljs-uuid.core :as uuid]))

(defn time []
  #+clj  (System/currentTimeMillis)
  #+cljs (.getTime (js/Date.)))

(defn uuid? [x]
  #+clj  (instance? java.util.UUID x)
  #+cljs (instance? cljs.core.UUID x))

(defn uuid []
  #+clj  (java.util.UUID/randomUUID)
  #+cljs (uuid/make-random))

(defn periodically [freq func]
  (let [ideal (/ 1000 freq)
        stop? (atom false)]
    #+clj  (future
             (loop []
               (when-not @stop?
                 (let [start (time)]
                   (func)
                   (let [duration (- (time) start)]
                     (Thread/sleep (max 0 (- ideal duration)))
                     (recur))))))
    #+cljs (letfn [(callback []
              (when-not @stop?
                (let [start (time)]
                  (func)
                  (let [duration (- (time) start)]
                    (js/setTimeout callback (max 0 (- ideal duration)))))))]
             (callback))
    #(reset! stop? true)))

(defn derive [h? tag & parents]
  (if (map? h?)
    (reduce #(core/derive %1 tag %2) h? parents)
    (doseq [p (cons tag parents)] (core/derive h? p))))

(def empty-state
  {:snapshot {}
   :index    {:eavt {}, :aevt {}, :avet {}}})

(defn facts [state]
  (for [[[e a v] t] (:snapshot state)] [e a v t]))

(defmulti -index
  (fn [key idx [o e a v t]] [key o]))

(defmethod -index [:eavt :assert] [_ idx [_ e a v t]]
  (assoc-in idx [e a v] t))

(defmethod -index [:aevt :assert] [_ idx [_ e a v t]]
  (assoc-in idx [a e v] t))

(defmethod -index [:avet :assert] [_ idx [_ e a v t]]
  (assoc-in idx [a v e] t))

(defmethod -index [:eavt :revoke] [_ idx [_ e a v _]]
  (dissoc-in idx [e a v]))

(defmethod -index [:aevt :revoke] [_ idx [_ e a v _]]
  (dissoc-in idx [a e v]))

(defmethod -index [:avet :revoke] [_ idx [_ e a v _]]
  (dissoc-in idx [a v e]))

(defn- build-index [key facts]
  (reduce (fn [idx [e a v t]] (-index key idx [:assert e a v t]))
          {} facts))

(defn reset [state facts]
  {:snapshot (into {} (for [[e a v t] facts] [[e a v] t]))
   :index    (into {} (for [k (keys (:index state))] [k (build-index k facts)]))})

(defn- update-snapshot [snapshot [o e a v t]]
  (case o
    :assert (assoc snapshot [e a v] t)
    :revoke (dissoc snapshot [e a v] t)))

(defn- update-index [index transition]
  (reduce (fn [i k] (assoc i k (-index k (i k) transition)))
          index
          (keys index)))

(defn update [state transition]
  (-> state
      (update-in [:snapshot] update-snapshot transition)
      (update-in [:index] update-index transition)))

(defn- transition-key [state [o e a v t]] [o a])

(defintent -valid?
  :dispatch transition-key
  :combine #(and %1 %2)
  :default ::invalid)

(defconduct -valid? ::invalid [_ _] false)

(defconduct -valid? [:assert ::live?] [_ [o e a v t]]
  (and (uuid? e) (integer? t) (true? v)))

(defconduct -valid? [:assert ::aspect] [s [o e a v t]]
  (and (uuid? e) (integer? t) (get-in s [:index :eavt e ::live?])))

(defconduct -valid? [:assert ::ref] [s [o e a v t]]
  (get-in s [:index :eavt v]))

(defconduct -valid? [:revoke ::live?] [_ [o e a v t]]
  (integer? t))

(defconduct -valid? [:revoke ::aspect] [_ [o e a v t]]
  (integer? t))

(defintent -react
  :dispatch transition-key
  :combine concat
  :default ::no-op)

(defconduct -react ::no-op [_ _] '())

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

(defconduct -react [:revoke ::live?] [s [o e a v t]]
  (concat (revoke-aspects s e t)
          (revoke-refs s e t)))

(defconduct -react [:assert ::singular] [s [o e a v t]]
  (for [v* (keys (get-in s [:index :eavt e a])) :when (not= v v*)]
    [:revoke e a v* t]))

(defn transition? [x]
  (and (sequential? x)
       (= (count x) 5)
       (let [[o e a v t] x]
         (and (#{:assert :revoke} o) (keyword? a)))))

(defn valid? [state transition]
  (and (transition? transition) (-valid? state transition)))

(defn react [state transition]
  (seq (-react state transition)))

(defn commit [state transition]
  (if (valid? state transition)
    (let [state     (update state transition)
          reactions (react state transition)]
      (reduce commit state reactions))
    state))

(defn tick [state time]
  (->> (:snapshot state)
       (mapcat (fn [[[e a _] _]] (react state [:tick e a time])))
       (reduce commit state)))
