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

    :Barista
    {:description "An order that has been placed."
     :fields
     {:email        {:type :String}
      :current_item {:type :OrderItem}
      :queue_length {:type :Int}}}}

   :queries
   {:baristaByEmail
    {:type        :Barista
     :description "Shows the current state of the Barista with the given email"
     :args        {:email {:type :String}}
     :resolve     :query/barista-by-email}}

   :mutations
   {:claimNextItem
    {:type        :Barista
     :description "Claim the next item from the general queue"
     :args        {:barista_email {:type :String}}
     :resolve     :mutation/claim-next-item!}

    :completeCurrentItem
    {:type        :Barista
     :description "Barista indicates that this item is complete"
     :args        {:barista_email {:type :String}
                   :item_id       {:type :ID}}
     :resolve     :mutation/complete-current-item!}}})

(defn barista-by-email
  [{:keys [api] :as context}
   {:keys [email] :as args}
   value]
  (api/barista-by-email api email))

(defn claim-next-item!
  [{:keys [api] :as context}
   {:keys [barista_email] :as args}
   value]
  (api/claim-next-item! api barista_email))

(defn complete-current-item!
  [{:keys [api] :as context}
   {:keys [item_id barista_email] :as args}
   value]
  (api/complete-current-item! api barista_email item_id))

(def resolver-map {:mutation/claim-next-item!       claim-next-item!
                   :mutation/complete-current-item! complete-current-item!
                   :query/barista-by-email          barista-by-email})

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
