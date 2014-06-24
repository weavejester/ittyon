(ns ittyon.core-test
  (:require #+clj  [clojure.test :refer :all]
            #+cljs [cemerick.cljs.test :as t :refer-macros [is deftest testing]]
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

(deftest test-revision?
  (is (not (i/revision? nil)))
  (is (not (i/revision? [:o :e :a :v :t])))
  (is (not (i/revision? [:assert :e "a" :v :t])))
  (is (not (i/revision? [:assert :e :a :v])))
  (is (i/revision? [:assert :e :a :v :t]))
  (is (i/revision? [:assert :e :a :v :t])))

(deftest test-valid?
  (let [system i/empty-system
        entity (i/uuid)
        time   (i/time)]
    (i/derive ::name ::i/aspect ::i/singular)
    (is (not (i/valid? system [:assert entity ::name "alice" time])))
    (is (not (i/valid? system [:assert entity ::i/live? false time])))
    (is (i/valid? system [:assert entity ::i/live? true time]))))

(deftest test-react
  (i/derive ::name ::i/aspect ::i/singular)
  (let [entity (i/uuid)
        time   (i/time)
        system (-> i/empty-system
                   (i/commit [:assert entity ::i/live? true time])
                   (i/commit [:assert entity ::name "alice" time]))]
    (is (= (i/react system [:assert entity ::name "bob" time])
           [[:revoke entity ::name "alice" time]]))
    (is (= (i/react system [:revoke entity ::i/live? true time])
           [[:revoke entity ::name "alice" time]]))))

(deftest test-commit
  (let [entity (i/uuid)
        time   (i/time)]
    (i/derive ::name ::i/aspect ::i/singular)
    (is (= (-> i/empty-system
               (i/commit [:assert entity ::i/live? true time])
               (i/commit [:assert entity ::name "alice" time])
               (i/commit [:assert entity ::name "bob" time])
               :state
               :snapshot)
           {[entity ::i/live? true] time
            [entity ::name "bob"] time}))))

(deftest test-recv-client
  (let [entity (i/uuid)
        time   (i/time)]
    (i/derive ::name  ::i/aspect ::i/singular)
    (is (= (-> i/empty-system
               (i/recv-client
                [:commit
                 [:assert entity ::i/live? true time]
                 [:assert entity ::name "alice" time]])
               :state
               :snapshot)
           {[entity ::i/live? true] time
            [entity ::name "alice"] time}))))

(deftest test-recv-server
  (let [entity (i/uuid)
        time   (i/time)]
    (i/derive ::name  ::i/aspect ::i/singular)
    (is (= (-> i/empty-system
               (i/recv-server
                [:commit
                 [:assert entity ::i/live? true time]
                 [:assert entity ::name "alice" time]])
               :state
               :snapshot)
           {[entity ::i/live? true] time
            [entity ::name "alice"] time}))))
