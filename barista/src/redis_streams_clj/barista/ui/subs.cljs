(ns redis-streams-clj.barista.ui.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::app-view
 (fn [{:keys [page]}]
   {:page page}))

(re-frame/reg-sub
 ::notifications
 (fn [{:keys [notifications]}]
   notifications))
