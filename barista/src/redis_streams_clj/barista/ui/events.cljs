(ns redis-streams-clj.barista.ui.events
  (:require [clojure.set :as set]
            [re-frame.core :as re-frame]
            [day8.re-frame.tracing :refer-macros [fn-traced]]
            [re-graph.core :as re-graph]
            redis-streams-clj.barista.ui.effects
            [redis-streams-clj.barista.ui.config :as config]
            [redis-streams-clj.barista.ui.core :as api]
            [redis-streams-clj.barista.ui.routes :as routes]))

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
   {:db       {:barista       nil
               :notifications {}
               :menu          {}}
    :dispatch [::re-graph/init {:ws-url   config/ws-url
                                :http-url config/http-url}]}))

(re-frame/reg-event-db
 :routes/home
 (fn-traced [db _]
   (assoc db :page :home)))

(re-frame/reg-event-fx
 :command/sign-in!
 (fn-traced [{:keys [db] :as fx} [_ params]]
   {:dispatch [::re-graph/query
               "query baristaByEmail($email:String){baristaByEmail(email:$email){email,queue_length,current_item{id,menu_item_id,customization,quantity,status}}}"
               {:email (:email params)}
               [:event/signed-in]]}))

(re-frame/reg-event-fx
 :event/signed-in
 (fn-traced [{:keys [db]} [_ result]]
   {:db         (if (seq (:errors result))
                  db
                  (update db :notifications assoc (random-uuid) {:color :info :message "Signed in!"}))
    :dispatch-n [[::re-graph/query
                  "query menu{menu{id,title,photo_url}}"
                  {}
                  [:query/menu]]
                 [:barista/updated :baristaByEmail result]]
    :navigate   (routes/work-queue)}))

(re-frame/reg-event-db
 :query/menu
 (fn-traced [db [_ {:keys [data errors] :as result}]]
   (prn :query/menu result)
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

(re-frame/reg-event-fx
 :command/claim-next-item!
 (fn-traced [{:keys [db] :as fx} [_]]
   {:dispatch [::re-graph/mutate
               "mutation claimNextItem($barista_email:String){claimNextItem(barista_email:$barista_email){email,queue_length,current_item{id,menu_item_id,customization,quantity,status}}}"
               {:barista_email (-> db :barista :email)}
               [:barista/updated :claimNextItem]]}))

(re-frame/reg-event-fx
 :command/complete-current-item!
 (fn-traced [{:keys [db] :as fx} [_ item-id]]
   {:dispatch [::re-graph/mutate
               "mutation completeCurrentItem($barista_email:String,$item_id:ID){completeCurrentItem(barista_email:$barista_email,item_id:$item_id){email,queue_length,current_item{id,menu_item_id,customization,quantity,status}}}"
               {:barista_email (-> db :barista :email)
                :item_id       item-id}
               [:barista/updated :completeCurrentItem]]}))

(re-frame/reg-event-db
 :barista/updated
 (fn-traced [db [_ result-key {:keys [data errors] :as result}]]
   (let [barista (get data result-key)]
     (if (seq errors)
       (update db :notifications #(reduce (fn [agg error]
                                            (let [id (random-uuid)]
                                              (assoc agg id error)))
                                          %
                                          (map api/format-error errors)))
       (assoc db :barista barista)))))

(re-frame/reg-event-db
 :routes/work-queue
 (fn-traced [db _]
   (assoc db :page :work-queue)))

(re-frame/reg-event-db
 :command/dismiss-notification
 (fn-traced [db [_ id]]
   (update db :notifications dissoc id)))
