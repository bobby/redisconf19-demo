(ns redis-streams-clj.storefront.api.service
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
            [redis-streams-clj.storefront.api.core :as api])
  (:import (clojure.lang IPersistentMap)))

(def schema
  {:enums
   {:OrderStatus
    {:description "The status of an order"
     :values      [:placed :ready :paid]}

    :OrderItemStatus
    {:description "The status of an order item"
     :values      [:basket :received :preparing :ready :delivered]}}

   :input-objects
   {:BasketItem
    {:description "An item from the menu in a customer's basket or in an order"
     :fields
     {:id            {:type :ID}
      :menu_item_id  {:type :ID}
      :customization {:type :String}
      :quantity      {:type :Int}}}}

   :objects
   {:MenuItem
    {:description "An item on the menu: a beverage or something"
     :fields
     {:id          {:type :ID}
      :title       {:type :String}
      :description {:type :String}
      :price       {:type        :Int
                    :description "Rental price in 1/100th USD."}
      :photo_url   {:type        :String
                    :description "The URL of the item photo"}}}

    :OrderItem
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
   {:menu
    {:type        '(list :MenuItem)
     :description "Show the menu"
     :resolve     :query/menu}}

   :mutations
   {:upsertCustomer
    {:type        :Customer
     :description "Insert a new Customer, or if one already exists with a given email, then update it"
     :args
     {:name  {:type :String}
      :email {:type :String}}
     :resolve     :mutation/upsert-customer!}

    :addItemsToBasket
    {:type        '(list :OrderItem)
     :description "Adds items to a customer's basket"
     :args
     {:customer_email {:type :String}
      :items          {:type '(list :BasketItem)}}
     :resolve     :mutation/add-items-to-basket!}

    :removeItemsFromBasket
    {:type        '(list :ID)
     :description "Removes items from a customer's basket"
     :args
     {:customer_email {:type :String}
      :items          {:type '(list :ID)}}
     :resolve     :mutation/remove-items-from-basket!}

    :placeOrder
    {:type        :Order
     :description "Places a new Order"
     :args
     {:customer_email {:type :String}
      :items          {:type '(list :BasketItem)}}
     :resolve     :mutation/place-order!}

    :payOrder
    {:type        :Order
     :description "Pay for an Order"
     :args
     {:id {:type :ID}}
     :resolve     :mutation/pay-order!}}

   :subscriptions
   {:customerByEmail
    {:type        :Customer
     :description "Query the Customer having the given email address"
     :args        {:email {:type :String}}
     :stream      api/customer-by-email-subscription}}})

(def resolver-map {:query/menu                         api/menu
                   :mutation/upsert-customer!          api/upsert-customer!
                   :mutation/add-items-to-basket!      api/add-items-to-basket!
                   :mutation/remove-items-from-basket! api/remove-items-from-basket!
                   :mutation/place-order!              api/place-order!
                   :mutation/pay-order!                api/pay-order!})

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
             ::http/secure-headers {:content-security-policy-settings {:object-src  "none"
                                                                       :default-src "'self' cdnjs.cloudflare.com unpkg.com"}})
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
