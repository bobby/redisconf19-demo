(ns redis-streams-clj.common.redis
  (:require [clojure.core.async :as async]
            [clojure.core.async.impl.protocols :as p]
            [com.stuartsierra.component :as component]
            [io.pedestal.log :as log]
            [taoensso.carmine :as car :refer (wcar)]
            [redis-streams-clj.common.util :as util]))

(defn publish-command
  [redis stream action data command-id]
  (log/info ::publish-command [stream action data])
  (let [command {:command/id        command-id
                 :command/action    action
                 :command/data      data
                 :command/timestamp (java.util.Date.)}
        offset  (wcar redis (car/xadd stream "*" command-id command))]
    (assoc command :redis/offset offset)))

(defn publish-event
  [redis stream action data event-id & [parent]]
  (log/info ::publish-event [stream action data parent])
  (let [event  {:event/id        event-id
                :event/parent    parent
                :event/action    action
                :event/data      data
                :event/timestamp (java.util.Date.)}
        offset (wcar redis (car/xadd stream "*" event-id event))]
    (assoc event :redis/offset offset)))

(defn create-consumer-group!
  [redis stream group]
  (= (wcar redis (car/xgroup "CREATE" stream group "$")) "OK"))

(defn- extract-stream-records
  [records]
  (map (fn [[offset [stream record]]]
         (assoc record
                :redis/offset offset
                :redis/stream stream))
       records))

(defn next-batch-from-stream-for-group
  [redis {:keys [stream client-id timeout batch-size]} group]
  (let [[[_ records]]
        (wcar redis (car/xreadgroup "GROUP" group client-id
                                    "BLOCK" (or timeout 0)
                                    "COUNT" (or batch-size 1)
                                    "STREAMS" stream
                                    ">"))]
    (extract-stream-records records)))

(defn next-batch-from-stream-starting-from-id
  [redis {:keys [stream timeout batch-size]} from-id]
  (let [[[_ records]]
        (wcar redis (car/xread "BLOCK" (or timeout 0)
                               "COUNT" (or batch-size 1)
                               "STREAMS" stream
                               from-id))]
    (extract-stream-records records)))

(defn acknowledge-stream-event
  [redis stream group redis-id]
  (wcar redis (car/xack stream group redis-id)))

(defrecord RedisStreamChannel [channel semaphore redis stream batch-size timeout start-id init]
  component/Lifecycle
  (start [component]
    (log/info :component ::RedisStreamChannel :phase :start)
    (let [semaphore (atom true)]
      (async/thread
        (loop [from-id (or start-id (:start-id init) "$")]
          (if @semaphore
            (let [events (next-batch-from-stream-starting-from-id redis
                                                                  {:stream     stream
                                                                   :batch-size batch-size
                                                                   :timeout    timeout}
                                                                  from-id)]
              (doseq [event events]
                (async/>!! channel event))
              (recur (-> events last :redis/offset)))
            :done)))
      (assoc component :semaphore semaphore)))
  (stop [component]
    (log/info :component ::RedisStreamChannel :phase :stop)
    (when semaphore (reset! semaphore false))
    (when channel (async/close! channel))
    (assoc component :channel nil))

  p/Channel
  (close!  [_] (p/close! channel))
  (closed? [_] (p/closed? channel))

  p/ReadPort
  (take! [_ fn1-handler] (p/take! channel fn1-handler)))

(defn make-redis-stream-channel
  ([stream-config]
   (make-redis-stream-channel stream-config nil))
  ([stream-config start-id]
   (make-redis-stream-channel stream-config start-id (async/chan 1)))
  ([{:keys [stream batch-size timeout redis]} start-id channel]
   (map->RedisStreamChannel {:channel    channel
                             :stream     stream
                             :batch-size batch-size
                             :timeout    timeout
                             :start-id   start-id
                             :redis      redis})))


(defn publish-error!
  [redis stream error parent]
  (publish-event redis stream :event/error error (util/uuid) parent))
