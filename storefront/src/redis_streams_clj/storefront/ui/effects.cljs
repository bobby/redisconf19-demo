(ns redis-streams-clj.storefront.ui.effects
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs-http.client :as http]
            [cljs.core.async :as async]
            [re-frame.core :as re-frame]
            [redis-streams-clj.storefront.ui.routes :as routes]))

(defn http-effect
  [request]
  (let [seq-request-maps (if (sequential? request) request [request])]
    (doseq [{:as   request
             :keys [on-success on-failure]
             :or   {on-success [:http-no-on-success]
                    on-failure [:http-no-on-failure]}}
            seq-request-maps]
      (let [ch (http/request request)]
        (go
          (let [response (async/<! ch)]
            (if (:success response)
              (re-frame/dispatch (conj on-success response))
              (re-frame/dispatch (conj on-failure response)))))))))

(re-frame/reg-fx :http http-effect)

(defn navigate-effect
  [path]
  (routes/navigate! path))

(re-frame/reg-fx :navigate navigate-effect)
