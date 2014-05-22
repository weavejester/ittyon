(ns user
  (:require [clojure.repl :refer :all]
            [clojure.pprint :refer [pprint]]
            [clojure.tools.namespace.repl :refer [refresh]]
            [criterium.core :refer [bench quick-bench]]
            [ittyon.core :as i]
            [medley.core :refer :all]))

(def sys i/empty-system)
