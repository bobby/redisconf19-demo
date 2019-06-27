(ns redis-streams-clj.inventory.api.service
  (:require [com.stuartsierra.component :as component]
            [io.pedestal.log :as log]
            [io.pedestal.interceptor :as interceptor]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.ring-middlewares :as ring-mw]
            [ring.util.response :as ring-resp]
            [ring.util.codec :as codec]
            [com.walmartlabs.lacinia.util :as lacinia-util]
            [com.walmartlabs.lacinia.schema :as ls]
            [com.walmartlabs.lacinia.pedestal :as lp]
            [com.walmartlabs.lacinia :as lacinia]
            [clojure.core.async :as async]
            [redis-streams-clj.common.util :as util]
            [redis-streams-clj.inventory.api.core :as api])
  (:import (clojure.lang IPersistentMap)))

(def schema
  {:input-objects
   {:InventoryLevelsInput
    {:description "The inventory levels for various ingredients, used for manually reconciling the inventory"
     :fields
     {:coffee_beans {:type :Int}
      :cocoa_powder {:type :Int}
      :milk         {:type :Int}}}}

   :objects
   {:InventoryLevels
    {:description "The inventory levels for various ingredients"
     :fields
     {:coffee_beans {:type :Int}
      :cocoa_powder {:type :Int}
      :milk         {:type :Int}}}

    :Inventory
    {:description "The inventory levels for various ingredients"
     :fields
     {:id     {:type :ID}
      :time   {:type :String}
      :levels {:type :InventoryLevels}}}}

   :queries
   {:currentInventory
    {:type        :Inventory
     :description "Show current inventory levels"
     :resolve     :query/current-inventory}

    :inventoryHistory
    {:type        '(list :Inventory)
     :description "Show inventory over time"
     :resolve     :query/inventory-history}}

   :mutations
   {:reconcileInventory
    {:type        :Inventory
     :description "Set the inventory levels, e.g. after conducting a manual inventory."
     :args
     {:inventory {:type :InventoryLevelsInput}}
     :resolve     :mutation/reconcile-inventory!}}

   :subscriptions
   {:inventoryUpdates
    {:type        :Inventory
     :description "Get continuously updating inventory levels"
     :args        {:email {:type :String}}
     :stream      api/inventory-subscription}}})

(defn current-inventory
  [{:keys [api] :as context} args value]
  (api/current-inventory api))

(defn inventory-history
  [{:keys [api] :as context} args value]
  (api/inventory-history api))

(defn reconcile-inventory!
  [{:keys [api] :as context} {:keys [levels] :as args} value]
  (api/reconcile-inventory! api levels))

(def resolver-map {:query/current-inventory       current-inventory
                   :query/inventory-history       inventory-history
                   :mutation/reconcile-inventory! reconcile-inventory!})

(defn health
  [_]
  {:status  200
   :headers {}
   :body    "healthy"})

(defn index
  [_]
  (-> "public/index.html"
      ring-resp/resource-response
      (ring-resp/content-type "text/html")))

(defn service-data
  [api {:keys [host port env join?] :as config}]
  (-> schema
      (lacinia-util/attach-resolvers resolver-map)
      ls/compile
      (lp/service-map
       {:env           env
        :graphiql      (= env :dev)
        :ide-path      "/graphiql"
        :subscriptions true
        :port          port
        :app-context   {:api api}})
      (assoc ::http/host  host
             ::http/join? join?
             ::http/resource-path "/public"
             ::http/secure-headers {:content-security-policy-settings
                                    {:style-src  "'self' 'unsafe-inline' cdnjs.cloudflare.com unpkg.com"
                                     :script-src "'self' 'unsafe-inline'"}})
      (merge (dissoc config :host :port :env :join?))
      (update ::http/routes conj
              ["/health" :get health :route-name ::health]
              ["/"       :get index  :route-name ::index]
              ["/*"      :get index  :route-name ::index-catchall])
      http/default-interceptors))

(defrecord Service [api config service-map]
  component/Lifecycle
  (start [component]
    (log/info :component ::Service :phase :start :config config)
    (log/debug :service component)
    (assoc component :service-map (service-data api config)))
  (stop [component]
    (log/info :component ::Service :phase :stop)
    (log/debug :service component)
    (assoc component :service-map nil)))

(defn make-service
  [config]
  (map->Service {:config config}))
