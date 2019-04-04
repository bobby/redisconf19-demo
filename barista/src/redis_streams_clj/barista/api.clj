(ns redis-streams-clj.barista.api
  (:gen-class)
  (:require [com.stuartsierra.component :as component]
            [io.pedestal.log :as log]
            [meta-merge.core :refer [meta-merge]]
            [redis-streams-clj.common.util :as util]
            [redis-streams-clj.barista.api.system :as system]
            [redis-streams-clj.barista.api.config :as config]))

(def prod-config {})

(defn -main
  "Main entrypoint for the application: discards the command-line
  arguments, sets the uncaught exception handler to log and fail fast,
  creates the system from the provided configuration, starts the
  system, and sets up a graceful shutdown of the system upon JVM
  shutdown."
  [& _]
  (log/info :application :start)
  (util/set-default-uncaught-exception-handler!)
  (-> config/config
      (meta-merge prod-config)
      system/make-system
      component/start
      util/add-shutdown-hook!))
