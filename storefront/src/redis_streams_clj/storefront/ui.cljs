(ns redis-streams-clj.storefront.ui
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [redis-streams-clj.storefront.ui.events :as events]
            [redis-streams-clj.storefront.ui.subs :as subs]
            [redis-streams-clj.storefront.ui.views :as views]
            [redis-streams-clj.storefront.ui.routes :as routes]))

(defn start []
  (re-frame/clear-subscription-cache!)
  (reagent/render [views/app-root]
                  (.getElementById js/document "app")))

(defn ^:export init []
  (re-frame/dispatch-sync [:app/initialize])
  (routes/app-routes re-frame/dispatch)
  (start))

(defn stop []
  (js/console.log "stop"))
