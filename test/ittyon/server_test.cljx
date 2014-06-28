(ns ittyon.server-test
  (:require #+clj  [clojure.test :refer :all]
            #+cljs [cemerick.cljs.test :as t :refer-macros [is deftest testing done]]
            [ittyon.core :as i]
            [ittyon.server :as server]))

(deftest test-recv-server
  (let [entity (i/uuid)
        time   (i/time)]
    (i/derive ::name  ::i/aspect ::i/singular)
    (is (= (-> i/empty-system
               (server/receive
                [:commit
                 [:assert entity ::i/live? true time]
                 [:assert entity ::name "alice" time]])
               :state
               :snapshot)
           {[entity ::i/live? true] time
            [entity ::name "alice"] time}))))
