(ns com.wsscode.pathom.viz.electron.background.server
  (:require
    [com.wsscode.electron.ipc-main :as ipc-main]
    [com.wsscode.socket-io.server :as io-server]))

(goog-define SERVER_PORT 8238)

(defn start! [env]
  (io-server/start-socket-io-server
    {::io-server/port
     SERVER_PORT

     ::io-server/on-client-connect
     (fn [env {::io-server/keys [client-connection] :as client}]
       (js/console.log "Client connected" client)
       (io-server/send-message client-connection "Hello dear"))

     ::io-server/on-client-message
     (fn [_ msg]
       (js/console.log "NEW MSG" msg))

     ::io-server/on-client-disconnect
     (fn [_ client])}))
