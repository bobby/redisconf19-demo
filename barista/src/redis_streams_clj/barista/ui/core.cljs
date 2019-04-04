(ns redis-streams-clj.barista.ui.core)

(defn format-error
  [{:keys [error] :as result}]
  {:color   :danger
   :message (:msg error)})
