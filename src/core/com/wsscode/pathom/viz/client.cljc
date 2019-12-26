(ns com.wsscode.pathom.viz.client
  (:require [com.fulcrologic.guardrails.core :refer [>def >defn >fdef => | <- ?]]
            [com.wsscode.socket-io.client :as sio-client]
            [clojure.spec.alpha :as s]))

(>def ::parser fn?)

(def default-config
  {::sio-client/server-url "http://localhost:8238"})

(defn connect [connector parser]
  {::sio-client/connector (sio-client/connect (merge default-config connector))
   ::parser               parser})
