(ns ittyon.async-test
  #+cljs
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [cemerick.cljs.test :refer [is deftest testing done]]
                   [intentions.core :refer [defconduct]])
  (:require #+clj  [clojure.test :refer :all]
            #+cljs [cemerick.cljs.test :as t]
            #+clj  [clojure.core.async :as a :refer [go]]
            #+cljs [cljs.core.async :as a]
            #+clj  [intentions.core :refer [defconduct]]
            [ittyon.core :as i]
            [ittyon.async :as ia]
            [chord.channels :refer [bidi-ch]]))

(i/derive ::name  ::i/aspect ::i/singular)
(i/derive ::email ::i/aspect ::i/singular)
(i/derive ::clock ::i/aspect ::i/singular)

(def entity (i/uuid))

(def system
  (-> i/empty-system
      (i/commit [:assert entity ::i/live? true (i/time)])
      (i/commit [:assert entity ::name "alice" (i/time)])
      (i/commit [:assert entity ::email "alice@example.com" (i/time)])
      (i/commit [:assert entity ::clock 0 (i/time)])))

(defn setup-server-client [system]
  (let [server  (ia/server system)
        a-ch    (a/chan)
        b-ch    (a/chan)
        client  (-> (ia/client i/empty-system)
                    (ia/connect (bidi-ch a-ch b-ch)))]
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
      (ia/send client [:assert entity ::name "bob"]
                      [:assert entity ::email "bob@example.com"])
      (Thread/sleep 25)
      (is (= (-> client :system deref :state :snapshot keys set)
             (-> server :system deref :state :snapshot keys set)
             #{[entity ::i/live? true]
               [entity ::name "bob"]
               [entity ::email "bob@example.com"]
               [entity ::clock 0]})))))

#+cljs
(deftest ^:async test-async
  (let [[server client] (setup-server-client system)]
    
    (go (testing "initial state transferred to client"
          (<! (a/timeout 25))
          (is (= (-> client :system deref :state :snapshot)
                 (-> server :system deref :state :snapshot))))

        (testing "client events relayed to server"
          (ia/send client [:assert entity ::name "bob"]
                   [:assert entity ::email "bob@example.com"])
          (<! (a/timeout 25))
          (is (= (-> client :system deref :state :snapshot keys set)
                 (-> server :system deref :state :snapshot keys set)
                 #{[entity ::i/live? true]
                   [entity ::name "bob"]
                   [entity ::email "bob@example.com"]
                   [entity ::clock 0]})))
        (done))))

(defconduct i/reactions [:tick ::clock] [s [o e a t1]]
  (let [[v t0] (first (get-in s [:index :eavt e a]))]
    [[:assert e a (+ v (- t1 t0)) t1]]))

#+clj
(deftest test-periodically
  (let [client (ia/client system)
        system (:system client)
        ticker (ia/periodically 100 #(swap! system i/tick))]
    (Thread/sleep 30)
    (is (> (-> @system :state :index :eavt (get entity) ::clock keys first)
           20))
    (Thread/sleep 30)
    (is (> (-> @system :state :index :eavt (get entity) ::clock keys first)
           40))
    (a/close! ticker)))

#+cljs
(deftest ^:async test-periodically
  (let [client (ia/client system)
        system (:system client)
        ticker (ia/periodically 100 #(swap! system i/tick))]
    (go (<! (a/timeout 30))
        (is (> (-> @system :state :index :eavt (get entity) ::clock keys first)
               20))
        (<! (a/timeout 30))
        (is (> (-> @system :state :index :eavt (get entity) ::clock keys first)
               40))
        (a/close! ticker)
        (done))))
