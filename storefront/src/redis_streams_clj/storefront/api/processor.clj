(ns redis-streams-clj.storefront.api.processor
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [io.pedestal.log :as log]
            [redis-streams-clj.common.util :as util]
            [redis-streams-clj.storefront.api.core :as api]))

(defmulti process-event
  (fn [api event]   (:event/action event))
  :default ::unknown)

(defrecord Processor [api event-mult event-channel]
  component/Lifecycle
  (start [component]
    (let [event-channel (async/chan 1)]
      (log/info :component ::Processor :phase :start)
      (log/debug :processor component)
      ;; Events
      (async/tap event-mult event-channel)
      (async/thread
        (loop []
          (if-some [event (async/<!! event-channel)]
            (do
              (log/debug ::process-event event)
              (process-event api event)
              (recur))
            :done)))
      (assoc component :event-channel event-channel)))
  (stop [component]
    (log/info :component ::Processor :phase :stop)
    (log/debug :processor component)
    (when event-channel
      (async/untap event-mult event-channel)
      (async/close! event-channel))
    (assoc component :event-channel nil)))

(defn make-processor
  []
  (map->Processor {}))

(defmethod process-event ::unknown
  [api event]
  (log/warn ::process-event ::unknown :event event))

(defmethod process-event :event/customer-created
  [api event]
  (api/publish-and-set-customer!
   api
   (-> event
       :event/data
       (util/add-stream-and-offset-from-event event))))

(defmethod process-event :event/items-added-to-basket
  [api {:keys [event/data] :as event}]
  (let [customer-before (api/customer-by-email api (:customer_email data))]
    (api/publish-and-set-customer!
     api
     (-> customer-before
         (update :basket merge (:items data))
         (util/add-stream-and-offset-from-event event)))))

(defmethod process-event :event/items-removed-from-basket
  [api {:keys [event/data] :as event}]
  (let [customer (api/customer-by-email api (:customer_email data))]
    (api/publish-and-set-customer!
     api
     (-> customer
         (update :basket util/dissoc-all (:items data))
         (util/add-stream-and-offset-from-event event)))))

(defmethod process-event :event/order-placed
  [api {:keys [event/data] :as event}]
  (let [customer (api/customer-by-email api (:customer_email data))
        order    (:order data)]
    (api/publish-and-set-customer!
     api
     (-> customer
         (assoc :basket {})
         (update :orders assoc (:id order) order)
         (util/add-stream-and-offset-from-event event)))))

(defmethod process-event :event/order-paid
  [api {:keys [event/data] :as event}]
  (let [customer (api/customer-by-email api (:customer_email data))
        order    (:order data)]
    (api/publish-and-set-customer!
     api
     (-> customer
         (update-in [:orders (:id order)] assoc :status :paid)
         (util/add-stream-and-offset-from-event event)))))
