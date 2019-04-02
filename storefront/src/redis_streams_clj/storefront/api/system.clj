(ns redis-streams-clj.storefront.api.system
  (:require [com.stuartsierra.component :as component]
            [io.pedestal.log :as log]
            [redis-streams-clj.common.redis :as redis]
            [redis-streams-clj.common.async :as async]
            [redis-streams-clj.storefront.api.service :as service]
            [redis-streams-clj.storefront.api.server :as server]
            [redis-streams-clj.storefront.api.processor :as processor]
            [redis-streams-clj.storefront.api.config :as config]
            [redis-streams-clj.storefront.api.core :as api]))

(defn make-system
  [{:keys [http redis command-stream event-stream customer-stream] :as config}]
  (component/system-map
   :event-channel    (redis/make-redis-stream-channel (assoc event-stream :redis redis))
   :event-mult       (component/using (async/make-mult) {:channel :event-channel})
   :api              (component/using (api/make-api config) [:event-mult :customer-pub])
   :service          (component/using (service/make-service http) [:api])
   :server           (component/using (server/make-server) [:service])
   :processor-init   (component/using (processor/make-init) [:api])
   :command-channel  (component/using (redis/make-redis-stream-channel command-stream) {:init :processor-init})
   :processor        (component/using (processor/make-processor) [:api :command-channel :event-mult])
   :customer-channel (redis/make-redis-stream-channel (assoc customer-stream :redis redis))
   :customer-pub     (component/using (async/make-pub {:topic-fn :email}) {:channel :customer-channel})))
