(ns redis-streams-clj.barista.api.system
  (:require [com.stuartsierra.component :as component]
            [io.pedestal.log :as log]
            [redis-streams-clj.common.redis :as redis]
            [redis-streams-clj.common.async :as async]
            [redis-streams-clj.barista.api.service :as service]
            [redis-streams-clj.barista.api.server :as server]
            [redis-streams-clj.barista.api.processor :as processor]
            [redis-streams-clj.barista.api.config :as config]
            [redis-streams-clj.barista.api.core :as api]))

(defn make-system
  [{:keys [http redis event-stream storefront-stream] :as config}]
  (component/system-map
   :storefront-channel (component/using (redis/make-redis-stream-channel storefront-stream) {:init :storefront-init})
   :event-channel      (redis/make-redis-stream-channel (assoc event-stream :redis redis))
   :event-mult         (component/using (async/make-mult) {:channel :event-channel})

   :storefront-init    (component/using (processor/make-storefront-init) [:api])
   :processor          (component/using (processor/make-processor) [:api :storefront-channel :event-mult])

   :api                (api/make-api config)
   :service            (component/using (service/make-service http) [:api])
   :server             (component/using (server/make-server) [:service])))
