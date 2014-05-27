(ns user
  (:require [clojure.repl :refer :all]
            [clojure.pprint :refer [pprint]]
            [clojure.tools.namespace.repl :refer [refresh]]
            [criterium.core :refer [bench quick-bench]]
            [ittyon.core :as i]
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

(defonce avatar (i/uuid))

(def sys
  (-> i/empty-system
      (i/commit [:assert avatar ::position [0 0] (i/time)])
      (i/commit [:assert avatar ::lifespan 10 (i/time)])))
