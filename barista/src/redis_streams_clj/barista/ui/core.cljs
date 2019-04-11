(ns redis-streams-clj.barista.ui.core)

(defn format-error
  [error]
  (assoc error :color :danger))
