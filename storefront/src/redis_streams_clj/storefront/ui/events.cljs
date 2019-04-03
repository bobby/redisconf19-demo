(ns redis-streams-clj.storefront.ui.events
  (:require [clojure.set :as set]
            [re-frame.core :as re-frame]
            [day8.re-frame.tracing :refer-macros [fn-traced]]
            [re-graph.core :as re-graph]
            redis-streams-clj.storefront.ui.effects
            [redis-streams-clj.storefront.ui.config :as config]
            [redis-streams-clj.storefront.ui.core :as api]
            [redis-streams-clj.storefront.ui.routes :as routes]))

(defn index-by-id
  [m]
  (reduce (fn [agg e]
            (assoc agg (:id e) e))
          {}
          m))

(defn- dissoc-all
  [m ks]
  (apply dissoc m ks))

(re-frame/reg-event-fx
 :app/initialize
 (fn-traced [fx _]
   {:db       {:menu          {}
               :notifications {}}
    ;; TODO: make these urls configurable
    :dispatch [::re-graph/init {:ws-url   config/ws-url
                                :http-url config/http-url}]}))

(re-frame/reg-event-db
 :routes/home
 (fn-traced [db _]
   (assoc db :page :home)))

(re-frame/reg-event-fx
 :routes/menu
 (fn-traced [{:keys [db]} [_ query-params]]
   {:db       (assoc db :page :menu)
    :dispatch [::re-graph/query
               "query menu{menu{id,title,description,price,photo_url}}"
               {}
               [:query/menu]]}))

(re-frame/reg-event-db
 :query/menu
 (fn-traced [db [_ {:keys [data errors] :as result}]]
   (if (seq errors)
     (update db :notifications #(reduce (fn [agg error]
                                          (let [id (random-uuid)]
                                            (assoc agg id error)))
                                        %
                                        (map api/format-error errors)))
     (update db :menu merge (reduce (fn [agg a]
                                      (assoc agg (:id a) a))
                                    {}
                                    (:menu data))))))

(re-frame/reg-event-db
 :routes/basket
 (fn-traced [db _]
   (assoc db :page :basket)))

(re-frame/reg-event-db
 :routes/orders
 (fn-traced [db _]
   (assoc db :page :orders)))

(re-frame/reg-event-fx
 :command/upsert-customer!
 (fn-traced [{:keys [db] :as fx} [_ params]]
   {:dispatch [::re-graph/mutate
               "mutation upsertCustomer($name:String,$email:String){upsertCustomer(name:$name,email:$email){id,name,email,basket{id,menu_item_id,customization,quantity},orders{id,items{id,menu_item_id,customization,quantity,status},status}}}"
               (select-keys params [:name :email])
               [:event/customer-upserted]]
    :db       (let [id (random-uuid)]
                (update db :notifications assoc id {:color :info :message "Signed in!"}))
    :navigate (routes/menu)}))

(re-frame/reg-event-fx
 :event/customer-upserted
 (fn-traced [{:keys [db]} [_ {:keys [data errors] :as result}]]
   (let [customer (:upsertCustomer data)]
     {:db       (if (seq errors)
                  (update db :notifications #(reduce (fn [agg error]
                                                       (let [id (random-uuid)]
                                                         (assoc agg id error)))
                                                     %
                                                     (map api/format-error errors)))
                  (assoc db :customer (-> customer
                                          (update :basket index-by-id)
                                          (update :orders index-by-id))))
      :dispatch [::re-graph/subscribe
                 (:email customer)
                 "subscription customerByEmail($email:String){customerByEmail(email:$email){id,name,email,basket{id,menu_item_id,customization,quantity},orders{id,items{id,menu_item_id,customization,quantity,status},status}}}"
                 {:email (:email customer)}
                 [:customer/updated]]})))

;; TODO: handle errors
(re-frame/reg-event-db
 :customer/updated
 (fn-traced [db [_ result]]
   (let [customer (-> result :data :customerByEmail)]
     (assoc db :customer (-> customer
                             (update :basket index-by-id)
                             (update :orders index-by-id))))))

(re-frame/reg-event-fx
 :command/add-items-to-basket!
 (fn-traced [{:keys [db] :as fx} [_ items]]
   {:dispatch [::re-graph/mutate
               "mutation addItemsToBasket($customer_email:String,$items:[BasketItem]){addItemsToBasket(customer_email:$customer_email,items:$items){id,menu_item_id,customization,quantity}}"
               {:customer_email (get-in db [:customer :email])
                :items          items}
               [:event/items-added-to-basket]]}))

;; TODO: handle :errors
(re-frame/reg-event-db
 :event/items-added-to-basket
 (fn-traced [db [_ {:keys [data] :as result}]]
   (-> db
       (assoc-in [:notifications (random-uuid)] {:color :success :message "Item added to basket!"})
       (update-in [:customer :basket] merge (-> data :addItemsToBasket index-by-id)))))

(re-frame/reg-event-fx
 :command/remove-items-from-basket!
 (fn-traced [{:keys [db] :as fx} [_ items]]
   {:dispatch [::re-graph/mutate
               "mutation removeItemsFromBasket($customer_email:String,$items:[ID]){removeItemsFromBasket(customer_email:$customer_email,items:$items)}"
               {:customer_email (get-in db [:customer :email])
                :items          items}
               [:event/items-removed-from-basket]]}))

;; TODO: handle :errors
(re-frame/reg-event-db
 :event/items-removed-from-basket
 (fn-traced [db [_ {:keys [data]}]]
   (-> db
       (assoc-in [:notifications (random-uuid)] {:color :success :message "Item removed from basket!"})
       (update-in [:customer :basket] dissoc-all (:removeItemsFromBasket data)))))

(re-frame/reg-event-fx
 :command/place-order!
 (fn-traced [{:keys [db] :as fx} [_]]
   (let [items (-> db :customer :basket vals)]
     {:dispatch [::re-graph/mutate
                 "mutation placeOrder($customer_email:String,$items:[BasketItem]){placeOrder(customer_email:$customer_email,items:$items){id,items{id,menu_item_id,customization,quantity,status},status}}"
                 {:customer_email (get-in db [:customer :email])
                  :items          items}
                 [:event/order-placed]]})))

;; TODO: handle :errors
(re-frame/reg-event-fx
 :event/order-placed
 (fn-traced [{:keys [db] :as fx} [_ {:keys [data] :as result}]]
   (let [order (:placeOrder data)]
     {:db       (-> db
                    (assoc-in [:customer :orders (:id order)] order)
                    (update :notifications assoc
                            (random-uuid) {:color   :info
                                           :message (str "Order #" (:id order) " Placed!")}))
      :navigate (routes/orders)})))

(re-frame/reg-event-fx
 :command/pay-order!
 (fn-traced [{:keys [db] :as fx} [_ id]]
   {:dispatch [::re-graph/mutate
               "mutation payOrder($id:ID){payOrder(id:$id){id,items,status}}"
               {:id id}
               [:event/order-paid]]}))

;; TODO: handle :errors
(re-frame/reg-event-db
 :event/order-paid
 (fn-traced [db [_ order]]
   (assoc-in db [:customer :orders (:id order)] order)))

(re-frame/reg-event-db
 :command/dismiss-notification
 (fn-traced [db [_ id]]
   (update db :notifications dissoc id)))
