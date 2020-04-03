(ns com.wsscode.pathom.viz.electron.background.server
  (:require
    [com.wsscode.pathom.viz.electron.ipc-main :as ipc-main]
    [com.wsscode.node-ws-server :as ws-server]))

(goog-define SERVER_PORT 8240)

(defn start! [env]
  (ws-server/start-ws!
    {::ws-server/port
     SERVER_PORT

     ::ws-server/on-client-connect
     (fn [env message]
       (js/console.log "Client connected")
       (cljs.pprint/pprint message)
       (cljs.pprint/pprint env)
       (ws-server/send-message! env [:hello/dear "Hello dear"]))

     ::ws-server/on-client-disconnect
     (fn [_ client]
       (js/console.log "get out"))

     ::ws-server/on-client-message
     (fn [_ msg]
       (js/console.log "NEW MSG" (cljs.pprint/pprint msg)))}))
