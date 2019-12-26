(ns com.wsscode.socket-io.server
  (:require
    ["socket.io" :as Server]
    [com.wsscode.transit :as t]
    [com.fulcrologic.guardrails.core :refer [>def >defn >fdef => | <- ?]]))

(>def ::port pos-int?)
(>def ::server-connection any?)
(>def ::client-connection any?)
(>def ::client-id string?)

(>def ::on-client-connect fn?)
(>def ::on-client-message fn?)
(>def ::on-client-disconnect fn?)

(defn send-message [emmiter msg]
  (.emit emmiter "event" #js {"transit-message" (t/write msg)}))

(defn setup-client-events
  [{::keys [clients on-client-message on-client-disconnect] :as config}
   {::keys [client-connection client-id]}]
  (.on client-connection "event"
    (fn [data reply-fn]
      (if on-client-message
        (on-client-message config
          {::client-id  client-id
           ::event-data data
           ::reply-fn   reply-fn}))))

  (.on client-connection "disconnect"
    (fn [data reply-fn]
      (println "DISCONNECT CLIENT")
      (if on-client-disconnect
        (on-client-disconnect config
          {::client-id  client-id
           ::event-data data
           ::reply-fn   reply-fn}))

      (swap! clients dissoc client-id)))

  nil)

(defn start-socket-io-server
  [{::keys [port on-client-connect] :as config}]
  (let [io      (Server)
        clients (atom {})
        config' (assoc config
                  ::server-connection io
                  ::clients clients)]
    (.on io "connection"
      (fn [client-connection]
        (let [client-id (gensym "client-id-")
              client    {::client-connection client-connection
                         ::client-id         client-id}]
          (swap! clients assoc client-id client)

          (if on-client-connect
            (on-client-connect config' client))

          (setup-client-events config' client))))

    (.listen io port)

    config'))
