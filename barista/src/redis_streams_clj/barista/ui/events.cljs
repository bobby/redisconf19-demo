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
   {:db       {:menu          {}
               :notifications {}}
    :dispatch [::re-graph/init {:ws-url   config/ws-url
                                :http-url config/http-url}]}))

(re-frame/reg-event-db
 :routes/home
 (fn-traced [db _]
   (assoc db :page :home)))

(re-frame/reg-event-db
 :routes/work-queue
 (fn-traced [db _]
   (assoc db :page :work-queue)))

(re-frame/reg-event-db
 :command/dismiss-notification
 (fn-traced [db [_ id]]
   (update db :notifications dissoc id)))
