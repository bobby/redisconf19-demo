(ns redis-streams-clj.storefront.ui.core)

(defn format-error
  [{:keys [error] :as result}]
  {:color   :danger
   :message (:msg error)})
