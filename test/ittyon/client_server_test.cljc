(ns ittyon.client-server-test
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go]]))
  (:require #?(:clj  [clojure.test :refer :all]
               :cljs [cljs.test :as t :refer-macros [is deftest testing async]])
            #?(:clj  [clojure.core.async :as a :refer [go <! >! <!! >!!]]
               :cljs [cljs.core.async :as a :refer [<! >!]])
            #?(:clj  [intentions.core :refer [defconduct]]
               :cljs [intentions.core :refer-macros [defconduct]])
            [clojure.set :as set]
            [ittyon.core :as i]
            [ittyon.client :as client]
            [ittyon.server :as server]
            [chord.channels :refer [bidi-ch]]))

(i/derive ::name      ::i/aspect ::i/singular)
(i/derive ::email     ::i/aspect ::i/singular)
(i/derive ::clock     ::i/aspect ::i/singular)
(i/derive ::selected? ::i/aspect ::i/singular)

(def entity (i/uuid))

(def init-state
  (i/state [[entity ::i/live? true (i/time)]
            [entity ::name "alice" (i/time)]
            [entity ::email "alice@example.com" (i/time)]
            [entity ::clock 0 (i/time)]]))

(defn connect-client! [server]
  (let [a-ch   (a/chan)
        b-ch   (a/chan)
        client (client/connect! (bidi-ch a-ch b-ch))]
    (server/accept! server (bidi-ch b-ch a-ch))
    client))

(deftest test-async
  #?(:clj
     (let [server (server/server init-state)
           client (<!! (connect-client! server))]

       (testing "identity of client"
         (i/uuid? (:id client)))
       
       (testing "initial state transferred to client"
         (is (= (-> client :state deref :snapshot keys set)
                (-> server :state deref :snapshot keys set))))

       (testing "connected client stored in state"
         (let [facts (-> server :state deref :snapshot keys set)]
           (is (contains? facts [(:id client) ::i/live? true]))
           (is (contains? facts [(:id client) ::client/connected? true]))))

       (testing "client events relayed to server"
         (client/transact! client [[:assert entity ::name "bob"]
                                   [:assert entity ::email "bob@example.com"]])
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
         (client/transact! client [^:local [:assert entity ::selected? true]])
         (Thread/sleep 25)
         (is (= (set/difference
                 (-> client :state deref :snapshot keys set)
                 (-> server :state deref :snapshot keys set))
                #{[entity ::selected? true]})))

       (testing "manual transition times"
         (reset! (:time-offset client) 0)
         (client/transact! client [[:assert entity ::clock 1 1234567890]])
         (Thread/sleep 25)
         (is (= (-> client :state deref :snapshot (get [entity ::clock 1]) first)
                1234567890))))

     :cljs
     (async done
       (go (let [server (server/server init-state)
                 client (<! (connect-client! server))]

             (testing "identity of client"
               (i/uuid? (:id client)))

             (testing "initial state transferred to client"
               (is (= (-> client :state deref :snapshot keys set)
                      (-> server :state deref :snapshot keys set))))

             (testing "connected client stored in state"
               (let [facts (-> server :state deref :snapshot keys set)]
                 (is (contains? facts [(:id client) ::i/live? true]))
                 (is (contains? facts [(:id client) ::client/connected? true]))))

             (testing "client events relayed to server"
               (client/transact! client [[:assert entity ::name "bob"]
                                         [:assert entity ::email "bob@example.com"]])
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
               ;; go loops in cljs.core.async erroneously eat bare metadata.
               ;; Until this bug is fixed, we need to use with-meta instead.
               (client/transact! client [(with-meta
                                           [:assert entity ::selected? true]
                                           {:local true})])
               (<! (a/timeout 25))
               (is (= (set/difference
                       (-> client :state deref :snapshot keys set)
                       (-> server :state deref :snapshot keys set))
                      #{[entity ::selected? true]})))

             (testing "manual transition times"
               (reset! (:time-offset client) 0)
               (client/transact! client [[:assert entity ::clock 1 1234567890]])
               (<! (a/timeout 25))
               (is (= (-> client :state deref :snapshot (get [entity ::clock 1]) first)
                      1234567890)))

             (done))))))

(deftest test-ping
  #?(:clj
     (do (testing "client"
           (let [ch     (a/chan)
                 client (client/connect! ch)]
             (>!! ch [:init {:id (i/uuid) :time (i/time) :reset #{}}])
             (let [time-offset (:time-offset (<!! client))]
               (is (<= 0 @time-offset 25))

               (>!! ch [:time (+ (i/time) 1000)])
               (Thread/sleep 25)
               (is (<= -1000 @time-offset -975))

               (>!! ch [:time (- (i/time) 1000)])
               (Thread/sleep 25)
               (is (<= 1000 @time-offset 1025)))))

         (testing "server"
           (let [server (-> (server/server init-state)
                            (assoc :ping-delay 25))
                 ch     (a/chan)]
             (server/accept! server ch)
             (is (= (first (<!! ch)) :init))
             (Thread/sleep 50)
             (is (= (first (<!! ch)) :time))
             (Thread/sleep 50)
             (is (= (first (<!! ch)) :time)))))

     :cljs
     (async done
       (go (testing "client"
             (let [ch     (a/chan)
                   client (client/connect! ch)]
               (>! ch [:init {:id (i/uuid) :time (i/time) :reset #{}}])
               (let [time-offset (:time-offset (<! client))]
                 (is (<= 0 @time-offset 25))

                 (>! ch [:time (+ (i/time) 1000)])
                 (<! (a/timeout 25))
                 (is (<= -1000 @time-offset -975))

                 (>! ch [:time (- (i/time) 1000)])
                 (<! (a/timeout 25))
                 (is (<= 1000 @time-offset 1025)))))

           (testing "server"
             (let [server (-> (server/server init-state)
                              (assoc :ping-delay 25))
                   ch     (a/chan)]
               (server/accept! server ch)
               (is (= (first (<! ch)) :init))
               (<! (a/timeout 50))
               (is (= (first (<! ch)) :time))
               (<! (a/timeout 50))
               (is (= (first (<! ch)) :time))
               (done)))))))

(deftest test-invalid
  #?(:clj
     (let [server      (server/server init-state)
           client      (<!! (connect-client! server))
           dead-entity (i/uuid)]

       (testing "invalid transitions from client"
         (is (thrown-with-msg?
              clojure.lang.ExceptionInfo #"Invalid transition for state"
              (client/transact! client [[:assert dead-entity ::name "invalid"]]))))

       (testing "invalid transitions from server"
         (let [invalid-entity (i/uuid)]
           (client/send! client [:transact [[:assert dead-entity ::name "invalid"]]])
           (Thread/sleep 25)
           (let [facts (-> server :state deref :snapshot keys set)]
             (is (not (contains? facts [dead-entity ::name "invalid"])))))))

     :cljs
     (async done
       (go
         (let [server      (server/server init-state)
               client      (<! (connect-client! server))
               dead-entity (i/uuid)]
           (testing "invalid transitions from client"
             (let [invalid-entity (i/uuid)]
               (is (thrown-with-msg?
                    cljs.core.ExceptionInfo #"Invalid transition for state"
                    (client/transact! client [[:assert invalid-entity ::name "invalid"]])))))

           (testing "invalid transitions"
             (let [invalid-entity (i/uuid)]
               (client/send! client
                             [:transact [[:assert invalid-entity ::name "invalid"]]])
               (<! (a/timeout 25))
               (let [facts (-> server :state deref :snapshot keys set)]
                 (is (not (contains? facts [invalid-entity ::name "invalid"]))))))

           (done))))))

(i/derive ::hire ::i/aspect ::i/singular)
(i/derive ::employee ::i/aspect ::i/singular ::i/ref)

(defconduct i/-react [:assert ::hire] [s [_ e a v t]]
  (let [e' (i/uuid)]
    [^:impure [:assert e' ::i/live? true t]
     ^:impure [:assert e' ::name v t]
     ^:impure [:assert e ::employee e' t]]))

(defn- get-employee [s e]
  (let [id   (-> s (get-in [:index :eavt e ::employee]) keys first)
        name (-> s (get-in [:index :eavt id ::name]) keys first)]
    {:id id, :name name}))

(deftest test-impure
  #?(:clj
     (let [server  (server/server init-state)
           client1 (<!! (connect-client! server))
           client2 (<!! (connect-client! server))
           entity  (i/uuid)]
       (client/transact! client1 [[:assert entity ::i/live? true]
                                  [:assert entity ::hire "bob"]])
       (Thread/sleep 25)
       (let [employee (-> server :state deref (get-employee entity))]
         (is (i/uuid? (:id employee)))
         (is (= (:name employee) "bob")))
       (is (= (-> server  :state deref :snapshot keys set)
              (-> client1 :state deref :snapshot keys set)
              (-> client2 :state deref :snapshot keys set))))

     :cljs
     (async done
       (go (let [server  (server/server init-state)
                 client1 (<! (connect-client! server))
                 client2 (<! (connect-client! server))
                 entity  (i/uuid)]
             (client/transact! client1 [[:assert entity ::i/live? true]
                                        [:assert entity ::hire "bob"]])
             (<! (a/timeout 25))
             (let [employee (-> server :state deref (get-employee entity))]
               (is (i/uuid? (:id employee)))
               (is (= (:name employee) "bob")))
             (is (= (-> server  :state deref :snapshot keys set)
                    (-> client1 :state deref :snapshot keys set)
                    (-> client2 :state deref :snapshot keys set)))
             (done))))))
