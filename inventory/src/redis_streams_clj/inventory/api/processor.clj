(ns redis-streams-clj.inventory.api.processor
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [io.pedestal.log :as log]
            [redis-streams-clj.common.util :as util]
            [redis-streams-clj.inventory.api.core :as api]))

(defmulti process-barista-event
  (fn [api event] (:event/action event))
  :default ::unknown)
(defmulti process-event
  (fn [api event] (:event/action event))
  :default ::unknown)

(defmethod process-barista-event ::unknown
  [api event]
  (log/warn ::process-barista-event ::unknown :event event))

(defmethod process-event ::unknown
  [api event]
  (log/warn ::process-event ::unknown :event event))

(defmethod process-barista-event :event/item-completed
  [api event]
  (api/publish-upstream-event! api event))

(defmethod process-event :event/item-completed
  [api {{item :item} :event/data :as event}]
  (api/publish-inventory! api (api/drawdown-current-inventory api item)))

(defmethod process-event :event/inventory-reconciled
  [api {{levels :levels} :event/data}]
  (api/publish-inventory! api levels))

(defn process-inventory-update
  [api inventory]
  (api/set-inventory! api inventory))

(defrecord Processor [api barista-channel event-channel inventory-mult inventory-channel]
  component/Lifecycle
  (start [component]
    (let [inventory-channel (async/chan)]
      (async/tap inventory-mult inventory-channel)
      (log/info :component ::Processor :phase :start)
      (log/debug :processor component)
      ;; Barista Events
      (async/thread
        (loop []
          (if-some [event (async/<!! barista-channel)]
            (do
              (log/debug ::process-barista-event event)
              (process-barista-event api event)
              (recur))
            :done)))
      ;; Events
      (async/thread
        (loop []
          (if-some [event (async/<!! event-channel)]
            (do
              (log/debug ::process-event event)
              (process-event api event)
              (recur))
            :done)))
      ;; Inventory Updates
      (async/thread
        (loop []
          (if-some [event (async/<!! inventory-channel)]
            (do
              (log/debug ::process-inventory-update event)
              (process-inventory-update api event)
              (recur))
            :done)))
      (assoc component :inventory-channel inventory-channel)))
  (stop [component]
    (log/info :component ::Processor :phase :stop)
    (log/debug :processor component)
    (when inventory-channel
      (async/close! inventory-channel)
      (async/untap inventory-mult inventory-channel))
    (assoc component :event-channel nil)))

(defn make-processor
  []
  (map->Processor {}))

(defrecord BaristaInit [api start-id]
  component/Lifecycle
  (start [component]
    (log/info :component ::BaristaInit :phase :start)
    (log/debug ::BaristaInit component)
    ;; TODO: lookup the first unprocessed barista event based on events topic
    (assoc component :start-id "$"))
  (stop [component]
    (log/info :component ::BaristaInit :phase :stop)
    (log/debug ::BaristaInit component)
    (assoc component :start-id nil)))

(defn make-barista-init
  []
  (map->BaristaInit {}))
