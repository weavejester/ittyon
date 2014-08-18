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

(defn derive [h? tag & parents]
  (if (map? h?)
    (reduce #(core/derive %1 tag %2) h? parents)
    (doseq [p (cons tag parents)] (core/derive h? p))))

(def empty-state
  {:snapshot {}
   :index    {:eavt {}, :aevt {}, :avet {}}})

(defn facts [state]
  (for [[[e a v] t] (:snapshot state)] [e a v t]))

(defmulti update-index
  (fn [key idx [o e a v t]] [key o]))

(defmethod update-index [:eavt :assert] [_ idx [_ e a v t]]
  (assoc-in idx [e a v] t))

(defmethod update-index [:aevt :assert] [_ idx [_ e a v t]]
  (assoc-in idx [a e v] t))

(defmethod update-index [:avet :assert] [_ idx [_ e a v t]]
  (assoc-in idx [a v e] t))

(defmethod update-index [:eavt :revoke] [_ idx [_ e a v _]]
  (dissoc-in idx [e a v]))

(defmethod update-index [:aevt :revoke] [_ idx [_ e a v _]]
  (dissoc-in idx [a e v]))

(defmethod update-index [:avet :revoke] [_ idx [_ e a v _]]
  (dissoc-in idx [a v e]))

(defn- build-index [key facts]
  (reduce (fn [idx [e a v t]] (update-index key idx [:assert e a v t]))
          {} facts))

(defn reset [state facts]
  {:snapshot (into {} (for [[e a v t] facts] [[e a v] t]))
   :index    (into {} (for [k (keys (:index state))] [k (build-index k facts)]))})

(defn- update-snapshot [snapshot [o e a v t]]
  (case o
    :assert (assoc snapshot [e a v] t)
    :revoke (dissoc snapshot [e a v] t)))

(defn- update-indexes [indexes transition]
  (reduce (fn [i k] (assoc i k (update-index k (i k) transition)))
          indexes
          (keys indexes)))

(defn update [state transition]
  (-> state
      (update-in [:snapshot] update-snapshot transition)
      (update-in [:index] update-indexes transition)))

(defn- transition-key [state [o e a v t]] [o a])

(defintent validate
  :dispatch transition-key
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
  :dispatch transition-key
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

(defn transition? [x]
  (and (sequential? x)
       (= (count x) 5)
       (let [[o e a v t] x]
         (and (#{:assert :revoke} o) (keyword? a)))))

(defn valid? [state transition]
  (and (transition? transition) (validate state transition)))

(defn react [state transition]
  (reactions state transition))

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

(def reverse-ref?
  (memoize (fn [a] (re-find #"^_" (name a)))))

(def reverse-reverse-ref
  (memoize (fn [a] (keyword (namespace a) (subs (name a) 1)))))

(def ref-aspect?
  (memoize (fn [a] (if (reverse-ref? a)
                    (isa? (reverse-reverse-ref a) ::ref)
                    (isa? a ::ref)))))

(defn- find-vt [s e a d]
  (if (reverse-ref? a)
    (get-in s [:index :avet (reverse-reverse-ref a) e] d)
    (get-in s [:index :eavt e a] d)))

(defmulti aspect-value
  (fn [a vt] a))

(defmethod aspect-value ::singular [a vt]
  (first (keys vt)))

(defmethod aspect-value :default [a vt]
  (mapv key (sort-by val vt)))

(defn find
  ([s e a] (find s e a nil))
  ([s e a d]
     (if (= a ::id)
       e
       (let [vt (find-vt s e a ::not-found)]
         (if (= vt ::not-found)
           d
           (if (reverse-ref? a)
             (aspect-value :default vt)
             (aspect-value a vt)))))))

#+clj
(deftype Entity [getter]
  clojure.lang.IFn
  (invoke [_ aspect] (getter aspect nil))
  (invoke [_ aspect not-found] (getter aspect not-found))
  clojure.lang.ILookup
  (valAt [_ aspect] (getter aspect nil))
  (valAt [_ aspect not-found] (getter aspect not-found)))

#+cljs
(deftype Entity [getter]
  IFn
  (-invoke [_ aspect] (getter aspect nil))
  (-invoke [_ aspect not-found] (getter aspect not-found))
  ILookup
  (-lookup [_ aspect] (getter aspect nil))
  (-lookup [_ aspect not-found] (getter aspect not-found)))

(declare entity)

(defn- find-ref [s e a d]
  (->> (find-vt s e a d)
       (map-keys #(entity s %))
       (aspect-value a)))

(defn entity [state id]
  (Entity.
   (memoize
    (fn [a d]
      (cond
       (= a ::id)      id
       (ref-aspect? a) (find-ref state id a d)
       :else           (find state id a d))))))

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
