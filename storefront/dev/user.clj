(ns ^:no-doc user
  (:require [clojure.repl :refer :all]
            [clojure.pprint :refer [pprint]]
            [clojure.tools.namespace.repl :refer [refresh set-refresh-dirs]]
            [clojure.java.io :as io]
            [io.pedestal.http :as http]
            [meta-merge.core :refer [meta-merge]]
            [com.stuartsierra.component :as component]
            [reloaded.repl :refer [system init start stop go reset]]
            [eftest.runner :as eftest]
            [redis-streams-clj.storefront.api.config :as config]
            [com.walmartlabs.lacinia :as lacinia]
            [com.walmartlabs.lacinia.schema :as ls]
            [com.walmartlabs.lacinia.pedestal :as lp]
            [com.walmartlabs.lacinia.util :as lacinia-util]
            [clojure.java.browse :refer [browse-url]]
            [clojure.walk :as walk])
  (:import (clojure.lang IPersistentMap)))

(set! *warn-on-reflection* true)

(def dev-config
  {:http {:env :dev
          ;; all origins are allowed in dev mode
          ::http/allowed-origins {:creds true :allowed-origins (constantly true)}
          ;; Content Security Policy (CSP) is mostly turned off in dev mode
          ::http/secure-headers {:content-security-policy-settings {:object-src "none"}}}})

(def config
  (-> config/config
      (meta-merge dev-config)
      (update-in [:redis :spec] dissoc :password)))

(ns-unmap *ns* 'test)

(defn test []
  (eftest/run-tests (eftest/find-tests "test") {:multithread? false}))

(when (io/resource "local.clj")
  (load "local"))

(defn dev
  []
  (require 'redis-streams-clj.storefront.api.system)
  (reloaded.repl/set-init! #((resolve 'redis-streams-clj.storefront.api.system/make-system) config)))
