(ns com.wsscode.socket-io.client
  (:require [com.fulcrologic.guardrails.core :refer [>def >defn >fdef => | <- ?]]
            [clojure.spec.alpha :as s]))

(>def ::server-url string?)
(>def ::connector (s/keys :req [::send-message]))

(>def ::connect fn?)
(>def ::send-message fn?)

(>def ::on-client-message fn?)

(def default-config
  {::server-url "http://localhost"})

(def ^String transfer-key "transit-message")

(defn connect [{::keys [connect] :as connector}]
  (connect connector))
