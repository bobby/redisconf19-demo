(ns redis-streams-clj.common.util
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [io.pedestal.log :as log]
            [clj-uuid :as uuid]))

(defn dissoc-all
  [m ks]
  (apply dissoc m ks))

(defn await-event-with-parent
  [{:keys [event-mult] :as api} parent-id]
  (let [ch (async/chan 1 (filter #(= (:event/parent %) parent-id)))]
    (async/tap event-mult ch)
    (async/go
      (let [event (async/<! ch)]
        (log/debug ::await-event-with-parent parent-id :event event)
        (async/untap event-mult ch)
        event))))

(defn set-default-uncaught-exception-handler!
  "Sets the default uncaught exception handler to log the error and exit
  the JVM process."
  []
  (Thread/setDefaultUncaughtExceptionHandler
   (reify Thread$UncaughtExceptionHandler
     (uncaughtException [_ thread ex]
       (log/error :exception ex :thread thread)
       (System/exit 1)))))

(defn add-shutdown-hook!
  "Adds a shutdown hook that gracefully stops the running system."
  [system]
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. #(do
                                (log/info :application :stop)
                                (component/stop system)))))

(defn uuid
  "Generates a random uuid when passed no args. When passed a string,
  attempts to safely parse into a java.util.UUID, returning nil on failure."
  ([]
   (uuid/v1))
  ([uuid-str]
   (uuid/as-uuid uuid-str))
  ([uuid-namespace uuid-name]
   (uuid/v5 uuid-namespace uuid-name)))
