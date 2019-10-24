(ns redis-streams-clj.inventory.api.core
  (:require [clojure.java.io :as io]
            [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [io.pedestal.log :as log]
            [taoensso.carmine :as car :refer (wcar)]
            [com.walmartlabs.lacinia.resolve :refer (resolve-as)]
            [redis-streams-clj.common.domain :as domain]
            [redis-streams-clj.common.redis :as redis]
            [redis-streams-clj.common.util :as util])
  (:import [org.apache.commons.codec.digest DigestUtils]))

(defrecord Api [redis event-stream inventory-stream inventory-mult]
  component/Lifecycle
  (start [component]
    (log/info :component ::Api :phase :start)
    component)
  (stop [component]
    (log/info :component ::Api :phase :stop)
    component))

(defn make-api
  [{:keys [redis event-stream inventory-stream] :as config}]
  (map->Api {:redis            redis
             :event-stream     event-stream
             :inventory-stream inventory-stream}))

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

;;;; Service API

(def inventory-history-key "inventory-history")

(defn inventory-history
  [{:keys [redis] :as api}]
  (wcar redis (car/lrange inventory-history-key 0 -1)))

(defn current-inventory
  [{:keys [redis] :as api}]
  (let [length (wcar redis (car/llen inventory-history-key))]
    (wcar redis (car/lindex inventory-history-key (dec length)))))

(defn inventory-subscription
  [{:keys [inventory-mult] :as api} _ callback]
  (let [ch (async/chan 1)]
    (async/tap inventory-mult ch)
    (async/go-loop []
      (if-some [inventory (async/<! ch)]
        (do
          (callback inventory)
          (recur))
        (callback nil)))
    #(do
       (async/untap inventory-mult ch)
       (async/close! ch))))

(defn reconcile-inventory!
  [{:keys [redis event-stream] :as api} inventory]
  (redis/publish-event redis
                       (:stream event-stream)
                       :event/inventory-reconciled
                       {:levels inventory}
                       (util/uuid)
                       {:command/action :reconcile-inventory
                        :command/data   {:inventory inventory}}))

;; TODO: handle failure to publish to stream
(defn publish-inventory!
  [{:keys [redis inventory-stream] :as api} levels]
  (let [inventory {:id     (util/uuid)
                   :time   (java.util.Date.)
                   :levels levels}
        offset    (wcar redis (car/xadd (:stream inventory-stream)
                                        "*"
                                        (:id inventory)
                                        inventory))]
    (assoc inventory :offset offset)))

(defn set-inventory!
  [{:keys [redis event-stream] :as api} inventory]
  (log/debug ::set-inventory! inventory)
  (wcar redis (car/rpush inventory-history-key inventory)))

(defn drawdown-inventory
  [levels-before {:keys [menu_item_id quantity] :as order-item}]
  (let [adjustments (get domain/ingredient-usage menu_item_id)]
    (reduce-kv (fn [agg k v]
                 (update agg k - (* v quantity)))
               levels-before
               adjustments)))

(defn drawdown-current-inventory
  [api order-item]
  (drawdown-inventory (-> api current-inventory :levels)
                      order-item))

(comment

  (require '[redis-streams-clj.inventory.api.core :as api])
  (api/reconcile-inventory! (:api system) {:coffee_beans 1000 :cocoa_powder 4000 :milk 10000})

  )
