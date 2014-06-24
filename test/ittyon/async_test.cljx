(ns ittyon.async-test
  #+cljs
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [cemerick.cljs.test :refer [is deftest testing done]])
  (:require #+clj  [clojure.test :refer :all]
            #+cljs [cemerick.cljs.test :as t]
            #+clj  [clojure.core.async :as a :refer [go]]
            #+cljs [cljs.core.async :as a]
            [ittyon.core :as i]
            [ittyon.async :as ia]
            [chord.channels :refer [bidi-ch]]))

(i/derive ::name ::i/aspect ::i/singular)

(def entity (i/uuid))

(def system
  (-> i/empty-system
      (i/commit [:assert entity ::i/live? true (i/time)])
      (i/commit [:assert entity ::name "alice" (i/time)])))

(defn setup-server-client [system]
  (let [server  (ia/server system)
        a-ch    (a/chan)
        b-ch    (a/chan)
        client  (ia/connect (bidi-ch a-ch b-ch))]
    (ia/accept server (bidi-ch b-ch a-ch))
    [server client]))

#+clj
(deftest test-async
  (let [[server client] (setup-server-client system)]
    
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

#+cljs
(deftest ^:async test-async
  (let [[server client] (setup-server-client system)]
    (go (<! (a/timeout 25))
        (is (= (-> client :system deref :state :snapshot)
               (-> server :system deref :state :snapshot)))

        (ia/send client [:assert entity ::name "bob"])
        (<! (a/timeout 25))
        (is (= (-> client :system deref :state :snapshot keys set)
               (-> server :system deref :state :snapshot keys set)
               #{[entity ::i/live? true], [entity ::name "bob"]}))
        (done))))
