(ns redis-streams-clj.barista.api.processor
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [io.pedestal.log :as log]
            [redis-streams-clj.common.util :as util]
            [redis-streams-clj.barista.api.core :as api]))

(defmulti process-storefront-event
  (fn [api event]   (:event/action event))
  :default ::unknown)
(defmulti process-command
  (fn [api command] (:command/action command))
  :default ::unknown)
(defmulti process-event
  (fn [api event]   (:event/action event))
  :default ::unknown)

(defmethod process-storefront-event ::unknown
  [api event]
  (log/warn ::process-storefront-event ::unknown :event event))

(defmethod process-command ::unknown
  [api command]
  (log/warn ::process-command ::unknown :command command))

(defmethod process-event ::unknown
  [api event]
  (log/warn ::process-event ::unknown :event event))

(defrecord Processor [api command-channel event-mult event-channel storefront-channel]
  component/Lifecycle
  (start [component]
    (let [event-channel (async/chan 1)]
      (log/info :component ::Processor :phase :start)
      (log/debug :processor component)
      ;; Storefront Events
      (async/thread
        (loop []
          (if-some [event (async/<!! storefront-channel)]
            (do
              (log/debug ::process-storefront-event event)
              (process-storefront-event api event)
              (recur))
            :done)))
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

(defrecord StorefrontInit [api start-id]
  component/Lifecycle
  (start [component]
    (log/info :component ::StorefrontInit :phase :start)
    (log/debug ::StorefrontInit component)
    ;; TODO: lookup the first unprocessed storefront event based on events topic
    (assoc component :start-id "$"))
  (stop [component]
    (log/info :component ::StorefrontInit :phase :stop)
    (log/debug ::StorefrontInit component)
    (assoc component :start-id nil)))

(defn make-storefront-init
  []
  (map->StorefrontInit {}))

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
