(ns redis-streams-clj.barista.ui.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::app-view
 (fn [{:keys [page]}]
   {:page page}))

(re-frame/reg-sub
 ::notifications
 (fn [{:keys [notifications]}]
   notifications))

(defn present-order-item
  [order-item menu]
  (cond-> order-item
    true                       (dissoc :menu_item_id)
    (:menu_item_id order-item) (assoc :menu_item (get menu (:menu_item_id order-item)))))

(re-frame/reg-sub
 ::navbar
 (fn [{:keys [barista]}]
   (when barista
     {:queue_length (:queue_length barista)})))

(re-frame/reg-sub
 ::barista
 (fn [{:keys [barista menu]}]
   (update barista :current_item present-order-item menu)))
