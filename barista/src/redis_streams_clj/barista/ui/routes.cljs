(ns redis-streams-clj.barista.ui.routes
  (:require [secretary.core :as secretary :refer-macros [defroute]]
            [accountant.core :as accountant]))

(def ^:dynamic *dispatch*
  (fn [event] (prn :dispatch event)))

(defroute home "/" []
  (*dispatch* [:routes/home]))

(defroute work-queue "/work-queue" [query-params]
  (*dispatch* [:routes/work-queue query-params]))

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
