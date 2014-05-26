(ns user
  (:require [clojure.repl :refer :all]
            [clojure.pprint :refer [pprint]]
            [clojure.tools.namespace.repl :refer [refresh]]
            [criterium.core :refer [bench quick-bench]]
            [ittyon.core :as i]
            [medley.core :refer :all]))

(derive ::position ::i/aspect)

(defmethod i/validate [:assert ::position] [s [o e a v t]]
  (and (vector? v) (isa? (mapv type v) [Long Long])))

(defonce avatar (i/uuid))

(def sys
  (-> i/empty-system
      (i/commit [:assert avatar ::position [100 100] (i/time)])))
