(ns redis-streams-clj.barista.api.service
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
            [redis-streams-clj.barista.api.core :as api])
  (:import (clojure.lang IPersistentMap)))

(def schema
  {:enums
   {:OrderStatus
    {:description "The status of an order"
     :values      [:placed :ready :paid]}

    :OrderItemStatus
    {:description "The status of an order item"
     :values      [:basket :received :preparing :ready :delivered]}}

   :objects
   {:OrderItem
    {:description "An item from the menu in a customer's basket or in an order"
     :fields
     {:id            {:type :ID}
      :menu_item_id  {:type :ID}
      :customization {:type :String}
      :quantity      {:type :String}
      :status        {:type :OrderItemStatus}}}

    :Order
    {:description "An order that has been placed."
     :fields
     {:id     {:type :ID}
      :items  {:type '(list :OrderItem)}
      :status {:type :OrderStatus}}}

    :Customer
    {:description "A customer."
     :fields
     {:id     {:type :ID}
      :name   {:type :String}
      :email  {:type :String}
      :basket {:type '(list :OrderItem)}
      :orders {:type '(list :Order)}}}}

   :queries
   {}

   :mutations
   {}

   :subscriptions
   {}})

(def resolver-map {})

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
