(ns ittyon.client-server-test
  #+cljs
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [cemerick.cljs.test :refer [is deftest testing done]]
                   [intentions.core :refer [defconduct]])
  (:require #+clj  [clojure.test :refer :all]
            #+cljs [cemerick.cljs.test :as t]
            #+clj  [clojure.core.async :as a :refer [go <! <!!]]
            #+cljs [cljs.core.async :as a :refer [<!]]
            #+clj  [intentions.core :refer [defconduct]]
            [clojure.set :as set]
            [ittyon.core :as i]
            [ittyon.client :as client]
            [ittyon.server :as server]
            [chord.channels :refer [bidi-ch]]))

(i/derive ::name      ::i/aspect ::i/singular)
(i/derive ::email     ::i/aspect ::i/singular)
(i/derive ::clock     ::i/aspect ::i/singular)
(i/derive ::selected? ::i/aspect ::i/singular ::client/local)

(def entity (i/uuid))

(def init-state
  (i/state #{[entity ::i/live? true (i/time)]
             [entity ::name "alice" (i/time)]
             [entity ::email "alice@example.com" (i/time)]
             [entity ::clock 0 (i/time)]}))

(defn setup-server-client [state]
  (let [server  (server/server state)
        a-ch    (a/chan)
        b-ch    (a/chan)
        client  (client/connect! (bidi-ch a-ch b-ch))]
    (server/accept! server (bidi-ch b-ch a-ch))
    (go [server (<! client)])))

#+clj
(deftest test-async
  (let [[server client] (<!! (setup-server-client init-state))]

    (testing "identity of client"
      (i/uuid? (:id client)))
    
    (testing "initial state transferred to client"
      (is (= (-> client :state deref :snapshot)
             (-> server :state deref :snapshot))))

    (testing "connected client stored in state"
      (let [facts (-> server :state deref :snapshot keys set)]
        (is (contains? facts [(:id client) ::i/live? true]))
        (is (contains? facts [(:id client) ::client/connected? true]))))

    (testing "client events relayed to server"
      (client/send! client [:assert entity ::name "bob"]
                           [:assert entity ::email "bob@example.com"])
      (Thread/sleep 25)
      (is (= (-> client :state deref :snapshot keys set)
             (-> server :state deref :snapshot keys set)
             #{[(:id client) ::i/live? true]
               [(:id client) ::client/connected? true]
               [entity ::i/live? true]
               [entity ::name "bob"]
               [entity ::email "bob@example.com"]
               [entity ::clock 0]})))

    (testing "local events not relayed to server"
      (client/send! client [:assert entity ::selected? true])
      (Thread/sleep 25)
      (is (= (set/difference
              (-> client :state deref :snapshot keys set)
              (-> server :state deref :snapshot keys set))
             #{[entity ::selected? true]})))))

#+cljs
(deftest ^:async test-async
  (go (let [[server client] (<! (setup-server-client init-state))]

        (testing "identity of client"
          (i/uuid? (:id client)))

        (testing "initial state transferred to client"
          (is (= (-> client :state deref :snapshot)
                 (-> server :state deref :snapshot))))

        (testing "connected client stored in state"
          (let [facts (-> server :state deref :snapshot keys set)]
            (is (contains? facts [(:id client) ::i/live? true]))
            (is (contains? facts [(:id client) ::client/connected? true]))))

        (testing "client events relayed to server"
          (client/send! client [:assert entity ::name "bob"]
                               [:assert entity ::email "bob@example.com"])
          (<! (a/timeout 25))
          (is (= (-> client :state deref :snapshot keys set)
                 (-> server :state deref :snapshot keys set)
                 #{[(:id client) ::i/live? true]
                   [(:id client) ::client/connected? true]
                   [entity ::i/live? true]
                   [entity ::name "bob"]
                   [entity ::email "bob@example.com"]
                   [entity ::clock 0]})))

        (testing "local events not relayed to server"
          (client/send! client [:assert entity ::selected? true])
          (<! (a/timeout 25))
          (is (= (set/difference
                  (-> client :state deref :snapshot keys set)
                  (-> server :state deref :snapshot keys set))
                 #{[entity ::selected? true]})))
        (done))))

#+clj
(deftest test-ping
  (let [server (-> (server/server init-state)
                   (assoc :ping-delay 25))
        ch     (a/chan)]
    (server/accept! server ch)
    (is (= (first (<!! ch)) :init))
    (Thread/sleep 50)
    (is (= (first (<!! ch)) :time))
    (Thread/sleep 50)
    (is (= (first (<!! ch)) :time))))

#+cljs
(deftest ^:async test-ping
  (let [server (-> (server/server init-state)
                   (assoc :ping-delay 25))
        ch     (a/chan)]
    (go (server/accept! server ch)
        (is (= (first (<! ch)) :init))
        (<! (a/timeout 50))
        (is (= (first (<! ch)) :time))
        (<! (a/timeout 50))
        (is (= (first (<! ch)) :time))
        (done))))
