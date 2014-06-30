(ns ittyon.server-test
  (:require #+clj  [clojure.test :refer :all]
            #+cljs [cemerick.cljs.test :as t :refer-macros [is deftest testing done]]
            [ittyon.core :as i]
            [ittyon.server :as server]))

(deftest test-server
  (let [s (server/server i/empty-system)]
    (is (map? s))
    (is (= (set (keys s)) #{:system :sockets}))
    (is (= (-> s :system deref) i/empty-system))
    (is (= (-> s :sockets deref) #{}))))

(deftest test-receive
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

(deftest test-shutdown
  (let [s (server/server i/empty-system)]
    (server/shutdown s)
    (is (= (-> s :system deref) i/empty-system))
    (is (= (-> s :sockets deref) nil))))
