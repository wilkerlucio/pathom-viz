(ns com.wsscode.pathom.viz.electron.background.server
  (:require
    [com.wsscode.pathom.viz.electron.ipc-main :as ipc-main]
    [com.wsscode.node-ws-server :as ws-server]
    [com.fulcrologic.guardrails.core :refer [>def >defn >fdef => | <- ?]]
    [com.wsscode.transit :as wsst]
    [cljs.spec.alpha :as s]))

(goog-define SERVER_PORT 8240)

(>def ::web-contents
  "The window reference for the Electron application renderer instance"
  any?)

(>defn message-renderer!
  [{::keys [web-contents]} msg]
  [(s/keys :req [::web-contents]) any?
   => any?]
  (.send web-contents "event"
    #js {:transit-message (wsst/write msg)}))

(defn start-ws! [server]
  (ws-server/start-ws!
    {::ws-server/port
     SERVER_PORT

     ::ws-server/on-client-connect
     (fn [{::ws-server/keys [client-id] :as env} message]
       (js/console.log "Client connected")
       (cljs.pprint/pprint message)
       (cljs.pprint/pprint env)
       (message-renderer! server
         {:com.wsscode.pathom.viz.electron.renderer.main/message-type
          :com.wsscode.pathom.viz.electron.renderer.main/connect-client

          ::ws-server/client-id
          client-id})
       (ws-server/send-message! env [:hello/dear "Hello dear"]))

     ::ws-server/on-client-disconnect
     (fn [_ client]
       (js/console.log "get out"))

     ::ws-server/on-client-message
     (fn [_ msg]
       (js/console.log "NEW MSG" (cljs.pprint/pprint msg)))}))

(defn handle-renderer-message
  [server message]
  ;; LANDMARK: Hook up of incoming messages from Electron renderer
  (cljs.pprint/pprint ["RENDER MSG" server message]))

(defonce started* (atom false))

(>defn start! [{::keys [web-contents] :as server}]
  [(s/keys :req [::web-contents]) => any?]
  (when-not @started*
    (reset! started* true)

    (.on web-contents "dom-ready"
      (fn []
        (let [server (start-ws! server)]
          (ipc-main/on-ipc-main-event
            #(handle-renderer-message server %2)))))))
