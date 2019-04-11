(ns redis-streams-clj.barista.ui.effects
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs-http.client :as http]
            [cljs.core.async :as async]
            [re-frame.core :as re-frame]
            [redis-streams-clj.barista.ui.routes :as routes]))

(defn navigate-effect
  [path]
  (routes/navigate! path))

(re-frame/reg-fx :navigate navigate-effect)
