(ns ittyon.client
  "A client that communicates with a server to mirror its state."
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]]))
  (:require #?(:clj  [clojure.core.async :as a :refer [go go-loop <! >!]]
               :cljs [cljs.core.async :as a :refer [<! >!]])
            #?(:clj  [intentions.core :refer [defconduct]]
               :cljs [intentions.core :refer-macros [defconduct]])
            [ittyon.core :as i]
            [medley.core :as m]))

(derive :ittyon/connected? :ittyon/aspect)
(derive :ittyon/local?     :ittyon/aspect)

(defconduct i/-valid? [:assert :ittyon/connected?] [_ [_ _ _ v _]]
  (m/boolean? v))

(defconduct i/-valid? [:assert :ittyon/local?] [_ [_ _ _ v _]]
  (true? v))

(defn ^:no-doc log-exceptions [{:keys [logger]} f]
  (try
    (f)
    (catch #?(:clj clojure.lang.ExceptionInfo :cljs cljs.core.ExceptionInfo) ex
      (logger (str (m/ex-message ex) ": " (:transition (ex-data ex))))
      nil)))

(defmulti ^:no-doc receive!
  (fn [client event] (first event)))

(defmethod receive! :default [_ _] nil)

(defmethod receive! :transact [client [_ transitions]]
  (log-exceptions client
    #(swap! (:state client) i/transact transitions (remove (comp :impure meta)))))

(defmethod receive! :reset [client [_ facts]]
  (reset! (:state client) (i/state facts)))

(defmethod receive! :time [client [_ time]]
  (reset! (:time-offset client) (- (i/time) time)))

(defn ^:no-doc send! [client message]
  (a/put! (-> client :socket :out) message))

(defn- fill-transition-times [transitions offset]
  (let [time (i/time)]
    (for [tr transitions]
      (update tr 4 (fn [t] (+ (or t time) offset))))))

(defn transact!
  "Atomically update the client with an ordered collection of transitions, then
  relay them to the server. Times may be omitted from the transitions, in which
  case the current time will be used. Transitions tagged as `:local` will not
  be sent to the server. Reaction transitions tagged as `:impure` will not be
  applied directly by clients, but will be relayed from the server in order to
  get a canonical value. See also: [[core/transact]]."
  [client transitions]
  (let [trans (fill-transition-times transitions @(:time-offset client))]
    (swap! (:state client) i/transact trans (remove (comp :impure meta)))
    (send! client [:transact (vec (remove (comp :local meta) trans))])))

(defn tick!
  "Move the clock forward on the client. This does not send anything to the
  server."
  [client]
  (swap! (:state client) i/tick (+ (i/time) @(:time-offset client))))

(defn- make-client
  [socket [event-type {:keys [id time reset]}]]
  {:pre [(= event-type :init)]}
  {:socket      socket
   :id          id
   :state       (atom (i/state (conj reset [id :ittyon/local? true time])))
   :time-offset (atom (- (i/time) time))
   :logger      println})

(defn connect!
  "Connect to a server via a socket, a map that contains `:in` and `:out` keys
  that hold the input and output channels. Returns a channel that promises to
  deliver a new client once the connection has been established. Used in
  conjuction with [[server/accept!]]."
  {:arglists '([socket])}
  [{:keys [in] :as socket}]
  (let [return (a/promise-chan)]
    (go (let [client (make-client socket (<! in))]
          (>! return client)
          (a/close! return)
          (loop []
            (when-let [event (<! in)]
              (receive! client event)
              (recur)))))
    return))
