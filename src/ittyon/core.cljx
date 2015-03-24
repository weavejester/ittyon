(ns ittyon.core
  "An in-memory immutable database designed to manage game state."
  (:refer-clojure :exclude [time derive update])
  #+clj
  (:require [clojure.core :as core]
            [medley.core :refer [dissoc-in map-keys map-vals]]
            [intentions.core :refer [defintent defconduct]])
  #+cljs
  (:require [cljs.core :as core]
            [medley.core :refer [dissoc-in map-keys map-vals]]
            [intentions.core :refer-macros [defintent defconduct]]
            [cljs-uuid.core :as uuid]))

(defn time
  "Return the current system time in milliseconds."
  []
  #+clj  (System/currentTimeMillis)
  #+cljs (.getTime (js/Date.)))

(defn uuid?
  "Return true if x is a UUID."
  [x]
  #+clj  (instance? java.util.UUID x)
  #+cljs (instance? cljs.core.UUID x))

(defn uuid
  "Return a random UUID."
  []
  #+clj  (java.util.UUID/randomUUID)
  #+cljs (uuid/make-random))

(defn periodically
  "Periodically evaluate a zero-argument function a specified number of times a
  second."
  [freq func]
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

(defn derive
  "Operates the same as clojure.core/derive, except that multiple parent
  arguments may be specified."
  [h? tag & parents]
  (if (map? h?)
    (reduce #(core/derive %1 tag %2) h? parents)
    (doseq [p (cons tag parents)] (core/derive h? p))))

(defn- transition-key [state [o e a v t]] [o a])

(derive ::indexed ::eavt ::aevt ::avet)
(derive ::aspect  ::indexed)
(derive ::live?   ::indexed)

(defintent -index
  "An intention to update the supplied index with a transition. Expected to
  return a function that updates the index. Dispatches off the transition op
  and the aspect. Composes the returned functions."
  {:arglists '([state transition])}
  :dispatch transition-key
  :combine comp)

(defconduct -index :default [_ _] identity)

(defconduct -index [:assert ::eavt] [_ [_ e a v t]]
  #(assoc-in % [:eavt e a v] t))

(defconduct -index [:assert ::aevt] [_ [_ e a v t]]
  #(assoc-in % [:aevt a e v] t))

(defconduct -index [:assert ::avet] [_ [_ e a v t]]
  #(assoc-in % [:avet a v e] t))

(defconduct -index [:revoke ::eavt] [_ [_ e a v _]]
  #(dissoc-in % [:eavt e a v]))

(defconduct -index [:revoke ::aevt] [_ [_ e a v _]]
  #(dissoc-in % [:aevt a e v]))

(defconduct -index [:revoke ::avet] [_ [_ e a v _]]
  #(dissoc-in % [:avet a v e]))

(defn index
  "Update the state's indexes for a single transition. Extend using the
  [[-index]] intention."
  [state transition]
  (update-in state [:index] (-index state transition)))

(defn update-snapshot
  "Update the state's snapshot for a single transition."
  [state [o e a v t]]
  (case o
    :assert (update-in state [:snapshot] assoc [e a v] t)
    :revoke (update-in state [:snapshot] dissoc [e a v] t)))

(defn update
  "Update a state with a single transition and return the new state. Combines
  [[update-snapshot]] with [[index]]."
  [state transition]
  (-> state
      (update-snapshot transition)
      (index transition)))

(defn state
  "Return a new state, either empty or prepopulated with a collection of facts."
  ([]      {:snapshot {}, :index {}})
  ([facts] (reduce update (state) (for [[e a v t] facts] [:assert e a v t]))))

(defn facts
  "Return a seq of facts held by the supplied state."
  [state]
  (for [[[e a v] t] (:snapshot state)] [e a v t]))

(defintent -valid?
  "An intention to determine whether a transition is valid for a particular
  state. Dispatches off the transition op and the aspect. Combines results of
  inherited keys with logical AND."
  {:arglists '([state transition])}
  :dispatch transition-key
  :combine #(and %1 %2))

(defconduct -valid? :default [_ _] false)

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
  "An intention that returns an ordered collection of reaction transitions,
  given a state and a valid transition. Dispatches off the transition op and
  the aspect. Concatenates the results of inherited keys."
  {:arglists '([state transition])}
  :dispatch transition-key
  :combine concat)

(defconduct -react :default [_ _] '())

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

(defn transition?
  "Return true if x is a transition. A transition is a vector of five values,
  commonly abbreviated to `[o e a v t]`. The op, o, is either `:assert` or
  `:revoke`. The aspect, a, must be a keyword."
  [x]
  (and (sequential? x)
       (= (count x) 5)
       (let [[o e a v t] x]
         (and (#{:assert :revoke} o) (keyword? a)))))

(defn valid?
  "Return true if the transition is a valid transition for the given state.
  Extend using the [[-valid?]] intention."
  [state transition]
  (and (transition? transition) (-valid? state transition)))

(defn react
  "Return a seq of reaction transitions, or nil, for a given state and
  transition. Extend using the [[-react]] intention."
  [state transition]
  (seq (-react state transition)))

(defn commit
  "Takes a state and a transition, and if the transition is valid, returns
  a new state with the transition and any reactions applied. If the transition
  is not valid for the state, the state is returned unchanged."
  [state transition]
  (if (valid? state transition)
    (let [state     (update state transition)
          reactions (react state transition)]
      (reduce commit state reactions))
    state))

(defn tick
  "Update a state by moving the clock forward to a new time. This may generate
  reactions that alter the state."
  [state time]
  (->> (:snapshot state)
       (mapcat (fn [[[e a _] _]] (react state [:tick e a time])))
       (reduce commit state)))

(defintent -effect!
  "An intention that asynchronously produces effect transitions via a callback.
  The callback expects a function that takes a state and returns an ordered
  collection of transitions. Dispatches off the transition op and the aspect."
  {:arglists '([callback transition])}
  :dispatch transition-key
  :combine (constantly nil))

(defconduct -effect! :default [_ _])

(declare effect!)

(defn- effect-callback [state-atom]
  (fn [f]
    (loop []
      (let [s @state-atom, ts (f s)]
        (if (compare-and-set! state-atom s (reduce commit s ts))
          (doseq [t ts]
            (effect! state-atom t))
          (recur))))))

(defn effect!
  "Takes an atom containing a state and a transition, then applies any
  asynchronous effects associated with the transition to the state atom."
  [state-atom transition]
  (-effect! (effect-callback state-atom) transition))

(defn transact!
  "Takes an atom containing a state and an ordered collection of transitions,
  commits each transition to the atom."
  [state-atom transitions]
  (swap! state-atom #(reduce commit % transitions))
  (doseq [t transitions]
    (effect! state-atom t)))
