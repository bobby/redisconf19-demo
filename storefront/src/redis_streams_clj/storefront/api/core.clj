(ns redis-streams-clj.storefront.api.core
  (:require [clojure.java.io :as io]
            [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [io.pedestal.log :as log]
            [taoensso.carmine :as car :refer (wcar)]
            [redis-streams-clj.common.domain :as domain]
            [redis-streams-clj.common.redis :as redis]
            [redis-streams-clj.common.util :as util])
  (:import [org.apache.commons.codec.digest DigestUtils]))

(defrecord Api [redis event-stream customer-stream customer-pub]
  component/Lifecycle
  (start [component]
    (log/info :component ::Api :phase :start)
    component)
  (stop [component]
    (log/info :component ::Api :phase :stop)
    component))

(defn make-api
  [{:keys [redis event-stream customer-stream] :as config}]
  (map->Api {:redis           redis
             :event-stream    event-stream
             :customer-stream customer-stream}))

(defn menu
  [api]
  domain/menu)

(declare present-order)
(defn present-customer
  [customer]
  (-> customer
      (update :basket vals)
      (update :orders (comp #(map present-order %) vals))))

(defn customer-by-email
  [{:keys [redis] :as api} email]
  (wcar redis (car/get email)))

(defn set-customer!
  [{:keys [redis] :as api} customer]
  (log/debug ::set-customer! customer)
  (wcar redis (car/set (:email customer) customer)))

;; Refactor this to just publish customer updates, which then get
;; updated in redis via processor
(defn publish-customer!
  [{:keys [redis customer-stream] :as api} customer]
  (let [id (wcar redis (car/xadd (:stream customer-stream)
                                 "*"
                                 (:id customer)
                                 customer))]
    (assoc customer :customer/offset id)))

;; Refactor this to just publish customer updates, which then get
;; updated in redis via processor
(defn publish-and-set-customer!
  [api customer]
  (->> customer
       (publish-customer! api)
       (set-customer! api)))

(defn upsert-customer!
  [{:keys [redis event-stream] :as api} customer-params]
  (log/info ::upsert-customer! customer-params)
  (if-some [customer (customer-by-email api (:email customer-params))]
    (present-customer customer)
    (redis/publish-event redis
                         (:stream event-stream)
                         :event/customer-created
                         (assoc customer-params
                                :id (util/uuid)
                                :basket {}
                                :orders {})
                         (util/uuid)
                         {:command/action :upsert-customer!
                          :command/data   customer-params})))

(defn item-maker
  [status]
  (fn [basket-items]
    (reduce (fn [agg {:keys [id
                             menu_item_id
                             customization
                             quantity]
                      :as   item}]
              (let [id (str (or id (util/uuid)))]
                (assoc agg id {:id            id
                               :menu_item_id  menu_item_id
                               :customization customization
                               :quantity      quantity
                               :status        status})))
            {}
            basket-items)))

(def make-basket-items
  (item-maker :basket))

(defn add-items-to-basket!
  [{:keys [redis event-stream] :as api} customer-email items]
  (log/info ::add-items-to-basket! [customer-email items])
  (when-let [customer (customer-by-email api customer-email)]
    (redis/publish-event redis
                         (:stream event-stream)
                         :event/items-added-to-basket
                         {:customer_email customer-email
                          :items          (make-basket-items items)}
                         (util/uuid)
                         {:command/action :add-items-to-basket
                          :command/data   {:customer_email customer-email
                                           :items          items}})))

(defn remove-items-from-basket!
  [{:keys [redis event-stream] :as api} customer-email item-ids]
  (log/info ::remove-items-from-basket! [customer-email item-ids])
  (when-let [customer (customer-by-email api customer-email)]
    (redis/publish-event redis
                         (:stream event-stream)
                         :event/items-removed-from-basket
                         {:customer_email customer-email
                          :items          item-ids}
                         (util/uuid)
                         {:command/action :remove-items-from-basket
                          :command/data   {:customer_email customer-email
                                           :items          item-ids}})))

(def make-order-items
  (item-maker :ordered))

(defn present-order
  [order]
  (update order :items vals))

;; TODO: CAS on items in basket?
(defn place-order!
  [{:keys [redis event-stream] :as api} customer-email items]
  (log/info ::place-order! [customer-email items])
  (when-let [customer (customer-by-email api customer-email)]
    (redis/publish-event redis
                         (:stream event-stream)
                         :event/order-placed
                         {:customer_email customer-email
                          :order          {:id     (util/uuid)
                                           :items  (make-order-items items)
                                           :status :placed}}
                         (util/uuid)
                         {:command/action :place-order
                          :command/data   {:customer_email customer-email
                                           :items          items}})))

(defn pay-order!
  [{:keys [redis event-stream] :as api} customer-email order-id]
  (log/info ::pay-order! [customer-email order-id])
  (let [data {:customer_email customer-email
              :order_id       order-id}]
    (when-let [customer (customer-by-email api customer-email)]
      (when (get-in customer [:orders order-id])
        (redis/publish-event redis
                             (:stream event-stream)
                             :event/order-paid
                             data
                             (util/uuid)
                             {:command/action :pay-order
                              :command/data   data})))))

(defn customer-by-email-subscription
  [{:keys [customer-pub] :as api} email callback]
  (let [ch (async/chan 1)]
    (async/sub customer-pub email ch)
    (async/go-loop []
      (if-some [customer (async/<! ch)]
        (do
          (callback (present-customer customer))
          (recur))
        (callback nil)))
    #(do
       (async/unsub customer-pub email ch)
       (async/close! ch))))

(defn publish-error!
  [{:keys [event-stream redis] :as api} error parent]
  (log/warn ::publish-error! [error parent])
  (redis/publish-error! redis (:stream event-stream) error parent))
