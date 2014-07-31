(ns ittyon.core-test
  (:require #+clj  [clojure.test :refer :all]
            #+cljs [cemerick.cljs.test :as t :refer-macros [is deftest testing done]]
            [ittyon.core :as i]))

(def eavt-state
  {:snapshot {[:e :a :v] :t}
   :index {:eavt {:e {:a {:v :t}}}
           :aevt {:a {:e {:v :t}}}
           :avet {:a {:v {:e :t}}}}})

(deftest test-from-snapshot
  (is (= (i/from-snapshot {[:e :a :v] :t})
         eavt-state)))

(deftest test-assert
  (is (= (i/assert i/empty-state [:e :a :v :t])
         eavt-state)))

(deftest test-revoke
  (is (= (i/revoke eavt-state [:e :a :v :t])
         i/empty-state)))

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

(deftest test-find
  (i/derive ::name ::i/aspect ::i/singular)
  (i/derive ::pet ::i/aspect)
  (i/derive ::child ::i/aspect ::i/ref)
  (let [parent-id (i/uuid)
        child-id  (i/uuid)
        time      (i/time)
        state     (-> i/empty-state
                      (i/assert [parent-id ::i/live? true time])
                      (i/assert [parent-id ::name "alice" time])
                      (i/assert [child-id ::i/live? true time])
                      (i/assert [child-id ::name "bob" time])
                      (i/assert [parent-id ::child child-id time])
                      (i/assert [parent-id ::pet "rover" (inc time)])
                      (i/assert [parent-id ::pet "rex" time]))]
    (is (= (i/find state parent-id ::i/id) parent-id))
    (is (= (i/find state parent-id ::name) "alice"))
    (is (= (i/find state parent-id ::pet) ["rex" "rover"]))
    (is (= (i/find state parent-id ::child) [child-id]))
    (is (= (i/find state child-id ::i/id) child-id))
    (is (= (i/find state child-id ::name) "bob"))
    (is (= (i/find state child-id ::_child) [parent-id]))))

(deftest test-entity
  (i/derive ::name ::i/aspect ::i/singular)
  (let [ent-id (i/uuid)
        time   (i/time)
        state  (-> i/empty-state
                   (i/assert [ent-id ::i/live? true time])
                   (i/assert [ent-id ::name "alice" time]))
        entity (i/entity state ent-id)]
    (is (= (entity ::i/id) ent-id))
    (is (= (get entity ::name) "alice"))
    (is (= (entity ::sex :female) :female))
    (is (= (get entity ::age 18) 18))))

(deftest test-revision?
  (is (not (i/revision? nil)))
  (is (not (i/revision? [:o :e :a :v :t])))
  (is (not (i/revision? [:assert :e "a" :v :t])))
  (is (not (i/revision? [:assert :e :a :v])))
  (is (i/revision? [:assert :e :a :v :t]))
  (is (i/revision? [:assert :e :a :v :t])))

(deftest test-valid?
  (let [engine i/empty-engine
        entity (i/uuid)
        time   (i/time)]
    (i/derive ::name ::i/aspect ::i/singular)
    (is (not (i/valid? engine [:assert entity ::name "alice" time])))
    (is (not (i/valid? engine [:assert entity ::i/live? false time])))
    (is (i/valid? engine [:assert entity ::i/live? true time]))))

(deftest test-react
  (i/derive ::name ::i/aspect ::i/singular)
  (let [entity (i/uuid)
        time   (i/time)
        engine (-> i/empty-engine
                   (i/commit [:assert entity ::i/live? true time])
                   (i/commit [:assert entity ::name "alice" time]))]
    (is (= (i/react engine [:assert entity ::name "bob" time])
           [[:revoke entity ::name "alice" time]]))
    (is (= (i/react engine [:revoke entity ::i/live? true time])
           [[:revoke entity ::name "alice" time]]))))

(deftest test-commit
  (let [entity (i/uuid)
        time   (i/time)]
    (i/derive ::name ::i/aspect ::i/singular)
    (is (= (-> i/empty-engine
               (i/commit [:assert entity ::i/live? true time])
               (i/commit [:assert entity ::name "alice" time])
               (i/commit [:assert entity ::name "bob" time])
               :state
               :snapshot)
           {[entity ::i/live? true] time
            [entity ::name "bob"] time}))))

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

(deftest test-refs
  (i/derive ::name ::i/aspect ::i/singular)
  (i/derive ::child ::i/aspect ::i/ref)
  (let [parent-id (i/uuid)
        child-id  (i/uuid)
        time      (i/time)
        engine    (-> i/empty-engine
                      (i/commit [:assert parent-id ::i/live? true time])
                      (i/commit [:assert parent-id ::name "alice" time])
                      (i/commit [:assert child-id ::i/live? true time])
                      (i/commit [:assert child-id ::name "bob" time])
                      (i/commit [:assert parent-id ::child child-id time]))
        engine*   (-> engine
                      (i/commit [:revoke child-id ::i/live? true time]))]
    (is (get-in engine [:state :snapshot [parent-id ::child child-id]]))
    (is (not (get-in engine* [:state :snapshot [parent-id ::child child-id]])))))
