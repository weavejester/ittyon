(ns user
  (:require [clojure.repl :refer :all]
            [clojure.pprint :refer [pprint]]
            [clojure.tools.namespace.repl :refer [refresh]]
            [criterium.core :refer [bench quick-bench]]
            [clojure.core.async :as a :refer [go <! >! <!! >!!]]
            [ittyon.core :as i]
            [ittyon.async :as ia]
            [chord.channels :refer [bidi-ch]]
            [medley.core :refer :all]))

(derive ::position ::i/aspect)
(derive ::lifespan ::i/aspect)

(defn coordinate? [v]
  (and (vector? v) (= (count v) 2) (every? number? v)))

(defmethod i/validate [:assert ::position] [s [o e a v t]]
  (coordinate? v))

(defmethod i/validate [:assert ::lifespan] [s [o e a v t]]
  (integer? v))

(defmethod i/reactions [:tick ::lifespan] [s [o e a t]]
  (let [[d t0] (first (get-in s [:index :eavt e a]))]
    (if (> t (+ t0 (* d 1000)))
      [[:assert e ::i/dead true t]])))

(def avatar (i/uuid))

(def client-system (atom i/empty-system))

(def init-system
  (-> i/empty-system
      (i/commit [:assert avatar ::position [0 0] (i/time)])))

(def server-system (atom init-system))

(let [a (a/chan)
      b (a/chan)]
  (ia/listen server-system (bidi-ch a b))
  (def client (ia/connect client-system (bidi-ch b a))))
