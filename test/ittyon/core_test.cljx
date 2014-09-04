(ns ittyon.core-test
  (:require #+clj  [clojure.test :refer :all]
            #+cljs [cemerick.cljs.test :as t :refer-macros [is deftest testing done]]
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

#+clj
(deftest test-periodically
  (let [counter (atom 0)
        stop    (i/periodically 100 #(swap! counter inc))]
    (Thread/sleep 30)
    (is (>= @counter 2))
    (stop)))

#+cljs
(deftest ^:async test-periodically
  (let [counter (atom 0)
        stop    (i/periodically 100 #(swap! counter inc))]
    (js/setTimeout (fn []
                     (is (>= @counter 2))
                     (stop)
                     (done))
                   30)))

(derive ::a ::i/aspect)

(def eavt-state
  {:snapshot {[:e ::a :v] :t}
   :index {:eavt {:e {::a {:v :t}}}
           :aevt {::a {:e {:v :t}}}
           :avet {::a {:v {:e :t}}}}})

(deftest test-state
  (testing "empty"
    (is (= (i/state) {:snapshot {}, :index {}})))
  (testing "not empty"
    (is (= (i/state #{[:e ::a :v :t]}) eavt-state))))

(deftest test-update
  (testing "assert"
    (is (= (i/update (i/state) [:assert :e ::a :v :t]) eavt-state)))
  (testing "revoke"
    (is (= (i/update eavt-state [:revoke :e ::a :v :t]) (i/state)))))

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
    (i/derive ::name ::i/aspect ::i/singular)
    (is (not (i/valid? state [:assert entity ::name "alice" time])))
    (is (not (i/valid? state [:assert entity ::i/live? false time])))
    (is (i/valid? state [:assert entity ::i/live? true time]))))

(deftest test-react
  (i/derive ::name ::i/aspect ::i/singular)
  (let [entity (i/uuid)
        time   (i/time)
        state  (i/state #{[entity ::i/live? true time]
                          [entity ::name "alice" time]})]
    (is (= (i/react state [:assert entity ::name "bob" time])
           [[:revoke entity ::name "alice" time]]))
    (is (= (i/react state [:revoke entity ::i/live? true time])
           [[:revoke entity ::name "alice" time]]))))

(deftest test-commit
  (let [entity (i/uuid)
        time   (i/time)]
    (i/derive ::name ::i/aspect ::i/singular)
    (is (= (-> (i/state)
               (i/commit [:assert entity ::i/live? true time])
               (i/commit [:assert entity ::name "alice" time])
               (i/commit [:assert entity ::name "bob" time])
               :snapshot)
           {[entity ::i/live? true] time
            [entity ::name "bob"] time}))))

(deftest test-refs
  (i/derive ::name ::i/aspect ::i/singular)
  (i/derive ::child ::i/aspect ::i/ref)
  (let [parent-id (i/uuid)
        child-id  (i/uuid)
        time      (i/time)
        state     (-> (i/state)
                      (i/commit [:assert parent-id ::i/live? true time])
                      (i/commit [:assert parent-id ::name "alice" time])
                      (i/commit [:assert child-id ::i/live? true time])
                      (i/commit [:assert child-id ::name "bob" time])
                      (i/commit [:assert parent-id ::child child-id time]))
        state*    (-> state
                      (i/commit [:revoke child-id ::i/live? true time]))]
    (is (get-in state [:snapshot [parent-id ::child child-id]]))
    (is (not (get-in state* [:snapshot [parent-id ::child child-id]])))))
