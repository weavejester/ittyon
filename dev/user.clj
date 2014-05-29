(ns user
  (:require [clojure.repl :refer :all]
            [clojure.pprint :refer [pprint]]
            [clojure.tools.namespace.repl :refer [refresh]]
            [criterium.core :refer [bench quick-bench]]
            [clojure.core.async :as a :refer [go <! >! <!! >!!]]
            [ittyon.core :as i]
            [ittyon.async :as ia]
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
      (for [[e avt] (-> s :index :eavt), [a vt] avt, [v _] vt]
        [:revoke e a v t]))))

(def avatar (i/uuid))

(def client-system
  (atom i/empty-system))

(def server-system
  (atom (-> i/empty-system (i/commit [:assert avatar ::position [0 0] (i/time)]))))

(def input-ch (a/chan))

(let [socket (a/chan)]
  (ia/listen socket server-system)
  (ia/connect socket client-system input-ch))
