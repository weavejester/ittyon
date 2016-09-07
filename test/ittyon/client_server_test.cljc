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
            [ittyon.server :as server]))

(i/derive ::name      :ittyon/aspect :ittyon/singular)
(i/derive ::email     :ittyon/aspect :ittyon/singular)
(i/derive ::clock     :ittyon/aspect :ittyon/singular)
(i/derive ::selected? :ittyon/aspect :ittyon/singular)

(def entity (i/uuid))

(def init-state
  (i/state [[entity :ittyon/live? true (i/time)]
            [entity ::name "alice" (i/time)]
            [entity ::email "alice@example.com" (i/time)]
            [entity ::clock 0 (i/time)]]))

(defn connect-client! [server]
  (let [a-ch   (a/chan)
        b-ch   (a/chan)
        client (client/connect! {:in a-ch :out b-ch})]
    (server/accept! server {:in b-ch :out a-ch})
    client))

(deftest test-async
  #?(:clj
     (let [server     (server/server init-state)
           client     (<!! (connect-client! server))
           local-fact [(:id client) :ittyon/local? true]]

       (testing "identity of client"
         (i/uuid? (:id client)))
       
       (testing "initial state transferred to client"
         (is (= (-> client :state deref :snapshot keys set (disj local-fact))
                (-> server :state deref :snapshot keys set))))

       (testing "connected client stored in state"
         (let [facts (-> server :state deref :snapshot keys set)]
           (is (contains? facts [(:id client) :ittyon/live? true]))
           (is (contains? facts [(:id client) :ittyon/connected? true]))))

       (testing "client has locality"
         (let [facts (-> client :state deref :snapshot keys set)]
           (is (contains? facts [(:id client) :ittyon/local? true]))))

       (testing "client events relayed to server"
         (client/transact! client [[:assert entity ::name "bob"]
                                   [:assert entity ::email "bob@example.com"]])
         (Thread/sleep 25)
         (is (= (-> client :state deref :snapshot keys set (disj local-fact))
                (-> server :state deref :snapshot keys set)
                #{[(:id client) :ittyon/live? true]
                  [(:id client) :ittyon/connected? true]
                  [entity :ittyon/live? true]
                  [entity ::name "bob"]
                  [entity ::email "bob@example.com"]
                  [entity ::clock 0]})))

       (testing "local events not relayed to server"
         (client/transact! client [^:local [:assert entity ::selected? true]])
         (Thread/sleep 25)
         (is (= (set/difference
                 (-> client :state deref :snapshot keys set (disj local-fact))
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
       (go (let [server     (server/server init-state)
                 client     (<! (connect-client! server))
                 local-fact [(:id client) :ittyon/local? true]]

             (testing "identity of client"
               (i/uuid? (:id client)))

             (testing "initial state transferred to client"
               (is (= (-> client :state deref :snapshot keys set (disj local-fact))
                      (-> server :state deref :snapshot keys set))))

             (testing "connected client stored in state"
               (let [facts (-> server :state deref :snapshot keys set)]
                 (is (contains? facts [(:id client) :ittyon/live? true]))
                 (is (contains? facts [(:id client) :ittyon/connected? true]))))

             (testing "client has locality"
               (let [facts (-> client :state deref :snapshot keys set)]
                 (is (contains? facts [(:id client) :ittyon/local? true]))))

             (testing "client events relayed to server"
               (client/transact! client [[:assert entity ::name "bob"]
                                         [:assert entity ::email "bob@example.com"]])
               (<! (a/timeout 25))
               (is (= (-> client :state deref :snapshot keys set (disj local-fact))
                      (-> server :state deref :snapshot keys set)
                      #{[(:id client) :ittyon/live? true]
                        [(:id client) :ittyon/connected? true]
                        [entity :ittyon/live? true]
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
                       (-> client :state deref :snapshot keys set (disj local-fact))
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
                 client (client/connect! {:in ch, :out ch})]
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
             (server/accept! server {:in ch, :out ch})
             (is (= (first (<!! ch)) :init))
             (Thread/sleep 50)
             (is (= (first (<!! ch)) :time))
             (Thread/sleep 50)
             (is (= (first (<!! ch)) :time)))))

     :cljs
     (async done
       (go (testing "client"
             (let [ch     (a/chan)
                   client (client/connect! {:in ch, :out ch})]
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
               (server/accept! server {:in ch, :out ch})
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

(i/derive ::hire     :ittyon/aspect :ittyon/singular)
(i/derive ::employee :ittyon/aspect :ittyon/singular :ittyon/ref)

(defconduct i/-react [:assert ::hire] [s [_ e a v t]]
  (let [e' (i/uuid)]
    [^:impure [:assert e' :ittyon/live? true t]
     ^:impure [:assert e' ::name v t]
     ^:impure [:assert e ::employee e' t]]))

(defn- get-employee [s e]
  (let [id   (-> s (get-in [:index :eavt e ::employee]) keys first)
        name (-> s (get-in [:index :eavt id ::name]) keys first)]
    {:id id, :name name}))

(deftest test-impure
  #?(:clj
     (let [server      (server/server init-state)
           client1     (<!! (connect-client! server))
           client2     (<!! (connect-client! server))
           local-fact1 [(:id client1) :ittyon/local? true]
           local-fact2 [(:id client2) :ittyon/local? true]
           entity      (i/uuid)]
       (client/transact! client1 [[:assert entity :ittyon/live? true]
                                  [:assert entity ::hire "bob"]])
       (Thread/sleep 25)

       (let [employee (-> server :state deref (get-employee entity))]
         (is (i/uuid? (:id employee)))
         (is (= (:name employee) "bob")))

       (is (= (-> server  :state deref :snapshot keys set)
              (-> client1 :state deref :snapshot keys set (disj local-fact1))
              (-> client2 :state deref :snapshot keys set (disj local-fact2))))

       (is (contains? (-> client1 :state deref :snapshot keys set) local-fact1))
       (is (contains? (-> client2 :state deref :snapshot keys set) local-fact2)))

     :cljs
     (async done
       (go (let [server      (server/server init-state)
                 client1     (<! (connect-client! server))
                 client2     (<! (connect-client! server))
                 local-fact1 [(:id client1) :ittyon/local? true]
                 local-fact2 [(:id client2) :ittyon/local? true]
                 entity      (i/uuid)]
             (client/transact! client1 [[:assert entity :ittyon/live? true]
                                        [:assert entity ::hire "bob"]])
             (<! (a/timeout 25))

             (let [employee (-> server :state deref (get-employee entity))]
               (is (i/uuid? (:id employee)))
               (is (= (:name employee) "bob")))

             (is (= (-> server  :state deref :snapshot keys set)
                    (-> client1 :state deref :snapshot keys set (disj local-fact1))
                    (-> client2 :state deref :snapshot keys set (disj local-fact2))))

             (is (contains? (-> client1 :state deref :snapshot keys set) local-fact1))
             (is (contains? (-> client2 :state deref :snapshot keys set) local-fact2))
             (done))))))
