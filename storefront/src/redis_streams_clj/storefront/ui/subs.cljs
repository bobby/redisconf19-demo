(ns redis-streams-clj.storefront.ui.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::app-view
 (fn [{:keys [page]}]
   {:page page}))

(re-frame/reg-sub
 ::menu
 (fn [{:keys [menu]}] menu))

(defn present-order-item
  [menu order-item]
  (-> order-item
      (dissoc :menu_item_id)
      (assoc :menu_item (get menu (:menu_item_id order-item)))))

(re-frame/reg-sub
 ::basket
 (fn [{:keys [customer menu]}]
   (let [items (map (partial present-order-item menu)
                    (-> customer :basket vals))]
     {:items items
      :total (reduce (fn [agg {:keys [quantity menu_item]}]
                       (+ agg (* quantity (:price menu_item))))
                     0
                     items)})))

(re-frame/reg-sub
 ::orders
 (fn [{:keys [customer menu]}]
   (for [[_ order] (:orders customer)]
     (let [items (map (partial present-order-item menu)
                      (:items order))]
       (assoc order
              :items items
              :total (reduce (fn [agg {:keys [quantity menu_item]}]
                               (+ agg (* quantity (:price menu_item))))
                             0
                             items))))))

(re-frame/reg-sub
 ::navbar
 (fn [{:keys [customer]}]
   {:basket-count (-> customer :basket count)}))

(re-frame/reg-sub
 ::notifications
 (fn [{:keys [notifications]}]
   notifications))
