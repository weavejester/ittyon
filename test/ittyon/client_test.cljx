(ns ittyon.client-test
  (:require #+clj  [clojure.test :refer :all]
            #+cljs [cemerick.cljs.test :as t :refer-macros [is deftest testing done]]
            [ittyon.core :as i]
            [ittyon.client :as client]))

(deftest test-receive
  (let [entity (i/uuid)
        time   (i/time)]
    (i/derive ::name  ::i/aspect ::i/singular)
    (is (= (-> i/empty-engine
               (client/receive
                [:commit
                 [:assert entity ::i/live? true time]
                 [:assert entity ::name "alice" time]])
               :state
               :snapshot)
           {[entity ::i/live? true] time
            [entity ::name "alice"] time}))))
