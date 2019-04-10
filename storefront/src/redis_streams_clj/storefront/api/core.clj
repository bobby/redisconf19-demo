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

(defrecord Api [redis command-stream event-stream event-mult customer-stream customer-pub]
  component/Lifecycle
  (start [component]
    (log/info :component ::Api :phase :start)
    component)
  (stop [component]
    (log/info :component ::Api :phase :stop)
    component))

(defn make-api
  [{:keys [redis event-stream command-stream customer-stream] :as config}]
  (map->Api {:redis           redis
             :command-stream  command-stream
             :event-stream    event-stream
             :customer-stream customer-stream}))

(defn menu
  [{:keys [api] :as context} args value]
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

(defn publish-customer-created!
  [{:keys [redis event-stream] :as api} customer parent]
  (log/debug ::customer-created! customer)
  (redis/publish-event redis
                       (:stream event-stream)
                       :event/customer-created
                       customer
                       (util/uuid)
                       parent))

(defn publish-customer!
  [{:keys [redis customer-stream] :as api} customer]
  (let [id (wcar redis (car/xadd (:stream customer-stream)
                                 "*"
                                 (:id customer)
                                 customer))]
    (assoc customer :customer/offset id)))

(defn publish-and-set-customer!
  [api customer]
  (->> customer
       (publish-customer! api)
       (set-customer! api)))

(defn upsert-customer!
  [{:keys [api] :as context} args value]
  (let [{:keys [redis command-stream]} api]
    (if-some [customer (customer-by-email api (:email args))]
      (present-customer customer)
      (let [command-id (util/uuid)
            ch         (util/await-event-with-parent api command-id)]
        (redis/publish-command redis
                               (:stream command-stream)
                               :command/create-customer
                               (assoc args
                                      :id (util/uuid)
                                      :basket {}
                                      :orders {})
                               command-id)
        (some-> ch
                async/<!!
                :event/data
                present-customer)))))

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
  [{:keys [api] :as context}
   {:keys [customer_email items] :as args}
   value]
  (let [{:keys [redis command-stream]} api]
    (redis/publish-command redis
                           (:stream command-stream)
                           :command/add-items-to-basket
                           {:customer_email customer_email
                            :items          (make-basket-items items)}
                           (util/uuid))
    nil))

(defn publish-items-added-to-basket!
  [{:keys [redis event-stream] :as api} data parent]
  (redis/publish-event redis
                       (:stream event-stream)
                       :event/items-added-to-basket
                       data
                       (util/uuid)
                       parent))

(defn remove-items-from-basket!
  [{:keys [api] :as context}
   {:keys [customer_email items] :as args}
   value]
  (let [{:keys [redis command-stream]} api]
    (redis/publish-command redis
                           (:stream command-stream)
                           :command/remove-items-from-basket
                           {:customer_email customer_email
                            :items          items}
                           (util/uuid))
    nil))

(defn publish-items-removed-from-basket!
  [{:keys [redis event-stream] :as api} data parent]
  (redis/publish-event redis
                       (:stream event-stream)
                       :event/items-removed-from-basket
                       data
                       (util/uuid)
                       parent))

(def make-order-items
  (item-maker :received))

(defn present-order
  [order]
  (update order :items vals))

(defn place-order!
  [{:keys [api] :as context}
   {:keys [customer_email items] :as args}
   value]
  (let [{:keys [redis command-stream]} api]
    (redis/publish-command redis
                           (:stream command-stream)
                           :command/place-order
                           {:customer_email customer_email
                            :items          (make-order-items items)}
                           (util/uuid))
    nil))

(defn publish-order-placed!
  [{:keys [redis event-stream] :as api} data parent]
  (redis/publish-event redis
                       (:stream event-stream)
                       :event/order-placed
                       data
                       (util/uuid)
                       parent))

(defn pay-order!
  [{:keys [api] :as context}
   {:keys [id customer_email] :as args}
   value]
  (let [{:keys [redis command-stream]} api
        command-id                     (util/uuid)
        ch                             (util/await-event-with-parent api command-id)]
    (redis/publish-command redis
                           (:stream command-stream)
                           :command/pay-order
                           {:customer_email customer_email
                            :id             id}
                           command-id)
    (some-> ch
            async/<!!
            :event/data
            :items
            vals)))

(defn publish-order-paid!
  [{:keys [redis event-stream] :as api} data parent]
  (redis/publish-event redis
                       (:stream event-stream)
                       :event/order-paid
                       data
                       (util/uuid)
                       parent))

(defn customer-by-email-subscription
  [{:keys [api] :as context} {:keys [email] :as args} callback]
  (let [{:keys [customer-pub]} api
        ch                     (async/chan 1)]
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
  [{:keys [redis event-stream] :as api} error parent]
  (redis/publish-event redis
                       (:stream event-stream)
                       :event/error
                       error
                       (util/uuid)
                       parent))
