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

(defrecord Api [redis command-stream event-stream event-mult]
  component/Lifecycle
  (start [component]
    (log/info :component ::Api :phase :start)
    component)
  (stop [component]
    (log/info :component ::Api :phase :stop)
    component))

(defn make-api
  [{:keys [redis event-stream command-stream] :as config}]
  (map->Api {:redis          redis
             :command-stream command-stream
             :event-stream   event-stream}))

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

(defn publish-item-claimed!
  [{:keys [redis event-stream] :as api}
   {:keys [command/data]
    :as   command}
   item]
  (redis/publish-event redis
                       (:stream event-stream)
                       :event/item-claimed
                       {:barista_email (:barista_email data)
                        :item          item}
                       (util/uuid)
                       (util/make-parent-from-upstream command)))

(defn publish-item-completed!
  [{:keys [redis event-stream] :as api}
   {:keys [command/id command/action command/data redis/stream]
    :as   command}
   item]
  (redis/publish-event redis
                       (:stream event-stream)
                       :event/item-completed
                       {:barista_email (:barista_email data)
                        :item          item}
                       (util/uuid)
                       (util/make-parent-from-upstream command)))

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
  [{:keys [redis command-stream] :as api} barista-email]
  (let [command-id (util/uuid)
        ch         (util/await-event-with-parent api command-id)]
    (redis/publish-command redis
                           (:stream command-stream)
                           :command/claim-next-item
                           {:barista_email barista-email}
                           command-id)
    (when-some [event (async/<!! ch)]
      (let [barista (barista-by-email api barista-email)]
        (if (= :event/error (:event/action event))
          (resolve-as barista (:event/data event))
          barista)))))

(defn complete-current-item!
  [{:keys [redis command-stream] :as api} barista-email item-id]
  (let [command-id (util/uuid)
        ch         (util/await-event-with-parent api command-id)]
    (redis/publish-command redis
                           (:stream command-stream)
                           :command/complete-current-item
                           {:item_id       item-id
                            :barista_email barista-email}
                           command-id)
    (when-some [event (async/<!! ch)]
      (let [barista (barista-by-email api barista-email)]
        (if (= :event/error (:event/action event))
          (resolve-as barista (:event/data event))
          barista)))))

(defn publish-error!
  [{:keys [event-stream redis] :as api} error parent]
  (redis/publish-error! redis (:stream event-stream) error parent))
