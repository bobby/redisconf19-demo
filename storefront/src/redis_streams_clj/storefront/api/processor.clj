(ns redis-streams-clj.storefront.api.processor
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [io.pedestal.log :as log]
            [redis-streams-clj.common.util :as util]
            [redis-streams-clj.storefront.api.core :as api]))

(defmulti process-command
  (fn [api command] (:command/action command))
  :default ::unknown)
(defmulti process-event
  (fn [api event]   (:event/action event))
  :default ::unknown)

(defrecord Processor [api command-channel event-mult event-channel]
  component/Lifecycle
  (start [component]
    (let [event-channel (async/chan 1)]
      (log/info :component ::Processor :phase :start)
      (log/debug :processor component)
      ;; Commands
      (async/thread
        (loop []
          (if-some [command (async/<!! command-channel)]
            (do
              (log/debug ::process-command command)
              (process-command api command)
              (recur))
            :done)))
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

(defrecord CommandInit [api start-id]
  component/Lifecycle
  (start [component]
    (log/info :component ::CommandInit :phase :start)
    (log/debug ::CommandInit component)
    ;; TODO: lookup the first unprocessed command based on events topic
    (assoc component :start-id "$"))
  (stop [component]
    (log/info :component ::CommandInit :phase :stop)
    (log/debug ::CommandInit component)
    (assoc component :start-id nil)))

(defn make-command-init
  []
  (map->CommandInit {}))

(defmethod process-command ::unknown
  [api command]
  (log/warn ::process-command ::unknown :command command))

(defmethod process-event ::unknown
  [api event]
  (log/warn ::process-event ::unknown :event event))

(defmethod process-command :command/create-customer
  [api command]
  (api/publish-customer-created! api (:command/data command) (util/make-parent-from-upstream command)))

(defmethod process-event :event/customer-created
  [api event]
  (api/publish-and-set-customer!
   api
   (-> event
       :event/data
       (util/add-stream-and-offset-from-event event))))

(defmethod process-command :command/add-items-to-basket
  [api {:keys [command/data] :as command}]
  (let [parent (util/make-parent-from-upstream command)]
    (if-let [customer (api/customer-by-email api (:customer_email data))]
      (api/publish-items-added-to-basket! api data parent)
      (api/publish-error! api
                          (assoc data :message "no customer found with this email")
                          parent))))

(defmethod process-event :event/items-added-to-basket
  [api {:keys [event/data] :as event}]
  (let [customer-before (api/customer-by-email api (:customer_email data))]
    (api/publish-and-set-customer!
     api
     (-> customer-before
         (update :basket merge (:items data))
         (util/add-stream-and-offset-from-event event)))))

(defmethod process-command :command/remove-items-from-basket
  [api {:keys [command/data] :as command}]
  (let [parent (util/make-parent-from-upstream command)]
    (if-let [customer (api/customer-by-email api (:customer_email data))]
      (api/publish-items-removed-from-basket! api data parent)
      (api/publish-error! api
                          (assoc data :message "no customer found with this email")
                          parent))))

(defmethod process-event :event/items-removed-from-basket
  [api {:keys [event/data] :as event}]
  (let [customer (api/customer-by-email api (:customer_email data))]
    (api/publish-and-set-customer!
     api
     (-> customer
         (update :basket util/dissoc-all (:items data))
         (util/add-stream-and-offset-from-event event)))))

;; TODO: CAS on items in basket?
(defmethod process-command :command/place-order
  [api {:keys [command/data] :as command}]
  (let [parent (util/make-parent-from-upstream command)]
    (if-let [customer (api/customer-by-email api (:customer_email data))]
      (api/publish-order-placed! api
                                 (assoc data :order {:id     (util/uuid)
                                                     :items  (:items data)
                                                     :status :placed})
                                 parent)
      (api/publish-error! api (assoc data :message "no customer found with this email") parent))))

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

(defmethod process-command :command/pay-order
  [api {:keys [command/data] :as command}]
  (let [parent (util/make-parent-from-upstream command)]
    (if-let [customer (api/customer-by-email api (:customer_email data))]
      (if (get-in customer [:orders (:id data)])
        (api/publish-order-paid! api data parent)
        (api/publish-error! api (assoc data :message "no order with id for customer") parent))
      (api/publish-error! api (assoc data :message "no customer found with this email") parent))))

(defmethod process-event :event/order-paid
  [api {:keys [event/data] :as event}]
  (let [customer (api/customer-by-email api (:customer_email data))
        order    (:order data)]
    (api/publish-and-set-customer!
     api
     (-> customer
         (update-in [:orders (:id order)] assoc :status :paid)
         (util/add-stream-and-offset-from-event event)))))
