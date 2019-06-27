(ns redis-streams-clj.inventory.api.server
  (:require [com.stuartsierra.component :as component]
            [io.pedestal.log :as log]
            [io.pedestal.http :as http]
            [redis-streams-clj.inventory.api.service :as service]))

(defrecord Server [service server]
  component/Lifecycle
  (start [component]
    (log/info :component ::Server :phase :start)
    (log/debug :server component)
    (let [server (http/create-server (:service-map service))]
      (assoc component :server (http/start server))))
  (stop [component]
    (log/info :component ::Server :phase :stop)
    (log/debug :server component)
    (when server (http/stop server))
    (assoc component :server nil)))

(defn make-server
  []
  (map->Server {}))
