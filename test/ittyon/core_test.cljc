(ns ittyon.core-test
  (:require #?(:clj  [clojure.test :refer :all]
               :cljs [cljs.test :as t :refer-macros [is deftest testing async]])
            #?(:clj  [intentions.core :refer [defconduct]]
               :cljs [intentions.core :refer-macros [defconduct]])
            [ittyon.core :as i]))

(deftest test-time
  (is (= (integer? (i/time)))))

(deftest test-uuid
  (is (i/uuid? (i/uuid)))
  (is (not= (i/uuid) (i/uuid))))

(deftest test-derive
  (is (= (i/derive (make-hierarchy) ::a ::b ::c)
         (-> (make-hierarchy)
             (derive ::a ::b)
             (derive ::a ::c)))))

(deftest test-periodically
  #?(:clj
     (let [counter (atom 0)
           stop    (i/periodically 100 #(swap! counter inc))]
       (Thread/sleep 30)
       (is (>= @counter 2))
       (stop))

     :cljs
     (async done
       (let [counter (atom 0)
             stop    (i/periodically 100 #(swap! counter inc))]
         (js/setTimeout (fn []
                          (is (>= @counter 2))
                          (stop)
                          (done))
                        30)))))

(derive ::a :ittyon/aspect)

(def eavt-state
  {:snapshot {[:e ::a :v] [:t 0]}
   :log '([:assert :e ::a :v :t])
   :count 1
   :index {:eavt {:e {::a {:v :t}}}
           :aevt {::a {:e {:v :t}}}
           :avet {::a {:v {:e :t}}}}})

(deftest test-state
  (testing "empty"
    (is (= (i/state) {:snapshot {}, :log (), :index {}, :count 0})))
  (testing "not empty"
    (is (= (i/state #{[:e ::a :v :t]}) eavt-state))))

(deftest test-update
  (testing "assert"
    (is (= (i/update (i/state) [:assert :e ::a :v :t]) eavt-state)))
  (testing "revoke"
    (is (= (i/update eavt-state [:revoke :e ::a :v :t])
           {:snapshot {}
            :log '([:revoke :e ::a :v :t] [:assert :e ::a :v :t])
            :index {}
            :count 2}))))

(deftest test-facts
  (is (= (i/facts eavt-state)
         [[:e ::a :v :t]]))
  (is (= (i/facts (i/update eavt-state [:assert :f ::a :v :t]))
         [[:e ::a :v :t] [:f ::a :v :t]])))

(deftest test-transition?
  (is (not (i/transition? nil)))
  (is (not (i/transition? [:o :e ::a :v :t])))
  (is (not (i/transition? [:assert :e "a" :v :t])))
  (is (not (i/transition? [:assert :e ::a :v])))
  (is (i/transition? [:assert :e ::a :v :t]))
  (is (i/transition? [:assert :e ::a :v :t])))

(deftest test-valid?
  (let [entity (i/uuid)
        time   (i/time)
        state  (i/state)]
    (i/derive ::name :ittyon/aspect :ittyon/singular)
    (is (not (i/valid? state [:assert entity ::name "alice" time])))
    (is (not (i/valid? state [:assert entity :ittyon/live? false time])))
    (is (i/valid? state [:assert entity :ittyon/live? true time]))))

(i/derive ::toggle :ittyon/aspect :ittyon/singular)

(defconduct i/-react [:assert ::toggle] [s [_ e a v t]]
  (if (get-in s [:index :eavt e a v])
    [[:revoke e a v t]]))

(deftest test-react
  (i/derive ::name :ittyon/aspect :ittyon/singular)
  (let [entity (i/uuid)
        time   (i/time)
        state  (i/state #{[entity :ittyon/live? true time]
                          [entity ::name "alice" time]})]
    (testing "singular"
      (is (= (i/react state [:assert entity ::name "bob" time])
             [[:revoke entity ::name "alice" time]])))
    (testing "live?"
      (is (= (i/react state [:revoke entity :ittyon/live? true time])
             [[:revoke entity ::name "alice" time]])))
    (testing "toggle"
      (let [transition [:assert entity ::toggle "foo" time]]
        (is (empty? (i/react state transition)))
        (is (= (i/react (i/update state transition) transition)
               [[:revoke entity ::toggle "foo" time]]))))))

(i/derive ::dice :ittyon/aspect :ittyon/singular)
(i/derive ::roll :ittyon/aspect :ittyon/singular)

(defconduct i/-react [:assert ::dice] [s [_ e a v t]]
  [[:revoke e a v t]
   ^:impure [:assert e ::roll (rand-int v) t]])

(deftest test-commit
  (let [entity (i/uuid)
        time   (i/time)]
    (i/derive ::name :ittyon/aspect :ittyon/singular)

    (testing "valid commit"
      (let [state (-> (i/state)
                      (i/commit [:assert entity :ittyon/live? true time])
                      (i/commit [:assert entity ::name "alice" time])
                      (i/commit [:assert entity ::name "bob" time])
                      (i/commit [:assert entity ::toggle "foo" time])
                      (i/commit [:assert entity ::toggle "foo" time]))]
        (is (= (:snapshot state)
               {[entity :ittyon/live? true] [time 0]
                [entity ::name "bob"]   [time 2]}))
        (is (= (:log state)
               (list [:revoke entity ::toggle "foo" time]
                     [:assert entity ::toggle "foo" time]
                     [:assert entity ::toggle "foo" time]
                     [:revoke entity ::name "alice" time]
                     [:assert entity ::name "bob" time]
                     [:assert entity ::name "alice" time]
                     [:assert entity :ittyon/live? true time])))))

    (testing "invalid commit"
      (is (thrown-with-msg?
           #?(:clj clojure.lang.ExceptionInfo :cljs cljs.core.ExceptionInfo)
           #"Invalid transition for state"
           (i/commit (i/state) [:assert entity ::name "alice" time]))))

    (testing "commit with reaction transducer"
      (let [trans [:assert entity ::dice 1000 time]
            state (i/commit (i/state) [:assert entity :ittyon/live? true time])]
        (is (not= (:snapshot (i/commit state trans)) (:snapshot state)))
        (is (= (:snapshot (i/commit state trans (remove (comp :impure meta))))
               (:snapshot state)))))))

(deftest test-tick
  (testing "last tick recorded"
    (is (= (-> (i/state) (i/tick 123456789) :last-tick)
           123456789))))

(deftest test-refs
  (i/derive ::name  :ittyon/aspect :ittyon/singular)
  (i/derive ::child :ittyon/aspect :ittyon/ref)
  (let [parent-id (i/uuid)
        child-id  (i/uuid)
        time      (i/time)
        state     (-> (i/state)
                      (i/commit [:assert parent-id :ittyon/live? true time])
                      (i/commit [:assert parent-id ::name "alice" time])
                      (i/commit [:assert child-id :ittyon/live? true time])
                      (i/commit [:assert child-id ::name "bob" time])
                      (i/commit [:assert parent-id ::child child-id time]))
        state*    (-> state
                      (i/commit [:revoke child-id :ittyon/live? true time]))]
    (is (get-in state [:snapshot [parent-id ::child child-id]]))
    (is (not (get-in state* [:snapshot [parent-id ::child child-id]])))))

(deftest test-transact
  (i/derive ::name :ittyon/aspect :ittyon/singular)
  (let [entity (i/uuid)
        time   (i/time)
        trans  [[:assert entity :ittyon/live? true time]
                [:assert entity ::name "alice" time]]
        state  (i/transact (i/state) trans)
        state' (i/transact state [[:assert entity ::name "bob" time]])]
    (is (= (:snapshot state)
           {[entity :ittyon/live? true] [time 0]
            [entity ::name "alice"] [time 1]}))
    (is (= (:last-transact state) trans))
    (is (= (:log state) (reverse trans)))
    (is (= (:last-transact state') [[:assert entity ::name "bob" time]]))
    (is (= (:log state')
           (list [:revoke entity ::name "alice" time]
                 [:assert entity ::name "bob" time])))))
