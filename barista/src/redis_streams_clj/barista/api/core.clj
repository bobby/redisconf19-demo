(ns redis-streams-clj.barista.api.core
  (:require [clojure.java.io :as io]
            [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [io.pedestal.log :as log]
            [taoensso.carmine :as car :refer (wcar)]
            [com.walmartlabs.lacinia.resolve :refer (resolve-as)]
            [redis-streams-clj.common.redis :as redis]
            [redis-streams-clj.common.util :as util])
  (:import [org.apache.commons.codec.digest DigestUtils]))

(defrecord Api [redis event-stream event-mult]
  component/Lifecycle
  (start [component]
    (log/info :component ::Api :phase :start)
    component)
  (stop [component]
    (log/info :component ::Api :phase :stop)
    component))

(defn make-api
  [{:keys [redis event-stream] :as config}]
  (map->Api {:redis        redis
             :event-stream event-stream}))

(defn publish-upstream-event!
  [{:keys [redis event-stream] :as api}
   {:keys [event/id event/action event/data redis/stream]
    :as   event}]
  (redis/publish-event redis
                       (:stream event-stream)
                       action
                       data
                       id
                       (util/make-parent-from-upstream event)))

(defn barista-queue-key
  [email]
  (str "barista-queue:" email))

(defn barista-completed-key
  [email]
  (str "barista-completed:" email))

(def general-queue-key "barista-general-queue")

(defn add-items-to-general-queue!
  [{:keys [redis] :as api} customer-email order-id items]
  (when (seq items)
    (wcar redis (apply car/lpush general-queue-key
                       (map #(assoc %
                                    :customer_email customer-email
                                    :order_id order-id)
                            items)))))

;; TODO: make await timeout configurable
(defn claim-next-general-queue-item!
  [{:keys [redis] :as api} email]
  (wcar redis (car/brpoplpush general-queue-key (barista-queue-key email) 1)))

;; TODO: make await timeout configurable
(defn complete-current-barista-queue-item!
  [{:keys [redis] :as api} email]
  (wcar redis (car/brpoplpush (barista-queue-key email) (barista-completed-key email) 1)))

;;;; Service API

(defn barista-by-email
  [{:keys [redis] :as api} email]
  (let [queue  (barista-queue-key email)
        length (wcar redis (car/llen queue))
        item   (wcar redis (car/lindex queue (dec length)))]
    {:email        email
     :current_item item
     :queue_length length}))

(defn claim-next-item!
  [{:keys [redis event-stream] :as api} barista-email]
  (if-some [item (claim-next-general-queue-item! api barista-email)]
    (do
      (redis/publish-event redis
                           (:stream event-stream)
                           :event/item-claimed
                           {:barista_email barista-email
                            :item          item}
                           (util/uuid)
                           {:command/action :claim-next-item
                            :command/data   {:barista_email barista-email}})
      (barista-by-email api barista-email))
    (resolve-as (barista-by-email api barista-email)
                {:message "No items to work on yet, please try again later!"})))

(defn complete-current-item!
  [{:keys [redis event-stream] :as api} barista-email item-id]
  (if-some [item (complete-current-barista-queue-item! api barista-email)]
    (do (redis/publish-event redis
                             (:stream event-stream)
                             :event/item-completed
                             {:barista_email barista-email
                              :item          item}
                             (util/uuid)
                             {:command/action :complete-current-item
                              :command/data   {:barista_email barista-email
                                               :item_id       item-id}})
        (barista-by-email api barista-email))
    (resolve-as (barista-by-email api barista-email)
                {:message "Can't complete item, since there is no item in your queue."})))

(defn publish-error!
  [{:keys [event-stream redis] :as api} error parent]
  (redis/publish-error! redis (:stream event-stream) error parent))
