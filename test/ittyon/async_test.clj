(ns ittyon.async-test
  (:require [clojure.test :refer :all]
            [ittyon.core :as i]
            [ittyon.async :as ia]
            [clojure.core.async :as a :refer [go <! >! <!! >!!]]
            [chord.channels :refer [bidi-ch]]))

(deftest test-async
  (i/derive ::name ::i/aspect ::i/singular)
  (let [entity  (i/uuid)
        system  (-> i/empty-system
                    (i/commit [:assert entity ::i/live? true (i/time)])
                    (i/commit [:assert entity ::name "alice" (i/time)]))
        server  (ia/server system)
        a-ch    (a/chan)
        b-ch    (a/chan)
        client  (ia/connect (bidi-ch a-ch b-ch))]
    
    (ia/accept server (bidi-ch b-ch a-ch))
    
    (testing "initial state transferred to client"
      (Thread/sleep 25)
      (is (= (-> client :system deref :state :snapshot)
             (-> server :system deref :state :snapshot))))

    (testing "client events relayed to server"
      (ia/send client [:assert entity ::name "bob"])
      (Thread/sleep 25)
      (is (= (-> client :system deref :state :snapshot keys set)
             (-> server :system deref :state :snapshot keys set)
             #{[entity ::i/live? true], [entity ::name "bob"]})))))
