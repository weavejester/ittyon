(ns ittyon.server-test
  (:require #+clj  [clojure.test :refer :all]
            #+cljs [cemerick.cljs.test :as t :refer-macros [is deftest testing done]]
            [ittyon.core :as i]
            [ittyon.server :as server]))

(deftest test-server
  (let [s (server/server i/empty-engine)]
    (is (map? s))
    (is (= (set (keys s)) #{:engine :sockets}))
    (is (= (-> s :engine deref) i/empty-engine))
    (is (= (-> s :sockets deref) #{}))))

(deftest test-receive
  (let [entity (i/uuid)
        time   (i/time)]
    (i/derive ::name  ::i/aspect ::i/singular)
    (is (= (-> i/empty-engine
               (server/receive
                [:commit
                 [:assert entity ::i/live? true time]
                 [:assert entity ::name "alice" time]])
               :state
               :snapshot)
           {[entity ::i/live? true] time
            [entity ::name "alice"] time}))))

(deftest test-shutdown
  (let [s (server/server i/empty-engine)]
    (server/shutdown s)
    (is (= (-> s :engine deref) i/empty-engine))
    (is (= (-> s :sockets deref) nil))))
