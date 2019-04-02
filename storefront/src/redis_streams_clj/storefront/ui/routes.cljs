(ns redis-streams-clj.storefront.ui.routes
  (:require [secretary.core :as secretary :refer-macros [defroute]]
            [accountant.core :as accountant]))

(def ^:dynamic *dispatch*
  (fn [event] (prn :dispatch event)))

(defroute home "/" []
  (*dispatch* [:routes/home]))

(defroute menu "/menu" [query-params]
  (*dispatch* [:routes/menu query-params]))

(defroute basket "/my/basket" []
  (*dispatch* [:routes/basket]))

(defroute orders "/my/orders" [query-params]
  (*dispatch* [:routes/orders query-params]))

(defn app-routes [dispatch]
  (accountant/configure-navigation!
   {:nav-handler
    (fn [path]
      (binding [*dispatch* dispatch]
        (secretary/dispatch! path)))
    :path-exists?
    (fn [path]
      (secretary/locate-route path))})
  (accountant/dispatch-current!))

(defn navigate! [path]
  (accountant/navigate! path))
