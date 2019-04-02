(ns redis-streams-clj.common.async
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as c]))

(defrecord PubComponent [channel topic-fn buf-fn pub]
  c/Lifecycle
  (start [this]
    (let [p (if buf-fn
              (async/pub channel topic-fn buf-fn)
              (async/pub channel topic-fn))]
      (assoc this :pub p)))
  (stop [this]
    (when pub (async/unsub-all pub))
    (assoc this :pub nil))

  async/Mux
  (muxch* [_] channel)

  async/Pub
  (sub* [_ v ch close?]
    (async/sub* pub v ch close?))
  (unsub* [_ v ch]
    (async/unsub* pub v ch))
  (unsub-all* [_]
    (async/unsub-all* pub))
  (unsub-all* [_ v]
    (async/unsub-all* pub v)))

(defn make-pub
  [{:keys [channel topic-fn buf-fn]}]
  (map->PubComponent {:channel  channel
                      :topic-fn topic-fn
                      :buf-fn   buf-fn}))

(defrecord MultComponent [channel mult]
  c/Lifecycle
  (start [this]
    (let [m (async/mult channel)]
      (assoc this :mult m)))
  (stop  [this]
    (when mult (async/untap-all mult))
    (assoc this :mult nil))

  async/Mux
  (muxch* [_] (async/muxch* mult))

  async/Mult
  (tap* [_ ch close?] (async/tap* mult ch close?))
  (untap* [_ ch] (async/untap* mult ch))
  (untap-all* [_] (async/untap-all* mult)))

(defn make-mult
  []
  (map->MultComponent {}))
