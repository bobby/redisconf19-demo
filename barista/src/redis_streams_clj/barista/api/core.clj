(ns redis-streams-clj.barista.api.core
  (:require [clojure.java.io :as io]
            [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [io.pedestal.log :as log]
            [taoensso.carmine :as car :refer (wcar)]
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
   {:keys [command/id command/action command/data redis/stream]
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

(def general-queue-key "general-queue")

(defn add-items-to-general-queue!
  [{:keys [redis] :as api} items]
  (when (seq items)
    (wcar redis (apply car/rpush general-queue-key items))))

(defn await-next-general-queue-item!
  [{:keys [redis] :as api}]
  (wcar redis (car/blpop general-queue-key)))

(defn pop-barista-queue-item!
  [{:keys [redis] :as api} email]
  (wcar redis (car/lpop (barista-queue-key email))))

(defn add-item-to-barista-queue!
  [{:keys [redis] :as api} email item]
  (wcar redis (car/rpush (barista-queue-key email) item)))

;;;; Service API

(defn barista-by-email
  [api email]
  (let [queue         (barista-queue-key email)
        [item length] (wcar (:redis api) (car/lindex queue 0) (car/llen queue))]
    {:email        email
     :current_item item
     :queue_length length}))

(defn claim-next-item!
  [{:keys [redis command-stream] :as api} barista-email]
  (let [
        command-id                     (util/uuid)
        ch                             (util/await-event-with-parent api command-id)]
    (redis/publish-command redis
                           (:stream command-stream)
                           :command/claim-next-item
                           {:barista_email barista-email}
                           command-id)
    (when-some [event (async/<!! ch)]
      (barista-by-email api barista-email))))

(defn complete-current-item!
  [api barista-email item-id]
  (let [{:keys [redis command-stream]} api
        command-id                     (util/uuid)
        ch                             (util/await-event-with-parent api command-id)]
    (redis/publish-command redis
                           (:stream command-stream)
                           :command/complete-current-item
                           {:item_id       item-id
                            :barista_email barista-email}
                           command-id)
    (when-some [event (async/<!! ch)]
      (barista-by-email api barista-email))))
