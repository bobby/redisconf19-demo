(ns redis-streams-clj.inventory.api.system
  (:require [com.stuartsierra.component :as component]
            [io.pedestal.log :as log]
            [redis-streams-clj.common.redis :as redis]
            [redis-streams-clj.common.async :as async]
            [redis-streams-clj.inventory.api.service :as service]
            [redis-streams-clj.inventory.api.server :as server]
            [redis-streams-clj.inventory.api.processor :as processor]
            [redis-streams-clj.inventory.api.config :as config]
            [redis-streams-clj.inventory.api.core :as api]))

(defn make-system
  [{:keys [http redis event-stream inventory-stream barista-stream] :as config}]
  (component/system-map
   :event-channel     (redis/make-redis-stream-channel (assoc event-stream :redis redis))

   :inventory-channel (redis/make-redis-stream-channel (assoc inventory-stream :redis redis))
   :inventory-mult    (component/using (async/make-mult) {:channel :inventory-channel})

   :barista-init      (component/using (processor/make-barista-init) [:api])
   :barista-channel   (component/using (redis/make-redis-stream-channel barista-stream) {:init :barista-init})
   :processor         (component/using (processor/make-processor) [:api :barista-channel :event-channel :inventory-mult])

   :api               (component/using (api/make-api config) [:inventory-mult])
   :service           (component/using (service/make-service http) [:api])
   :server            (component/using (server/make-server) [:service])))
