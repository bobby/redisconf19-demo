(ns redis-streams-clj.inventory.api.config
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [com.walmartlabs.dyn-edn :refer [env-readers]]))

(def config (->> "config.edn"
                 io/resource
                 slurp
                 (edn/read-string {:readers (env-readers)})))
