(ns com.wsscode.pathom.viz.electron.background.server
  (:require
    [cljs.spec.alpha :as s]
    [com.fulcrologic.guardrails.core :refer [>def >defn >fdef => | <- ?]]
    [com.wsscode.async.async-cljs :refer [go-promise <?]]
    [com.wsscode.async.processing :as wap]
    [com.wsscode.node-ws-server :as ws-server]
    [com.wsscode.pathom.viz.electron.ipc-main :as ipc-main]
    [com.wsscode.transit :as wsst]))

(goog-define SERVER_PORT 8240)

(>def ::web-contents
  "The window reference for the Electron application renderer instance"
  any?)

(>defn message-renderer!
  [{::keys [web-contents]} msg]
  [(s/keys :req [::web-contents]) any?
   => any?]
  (.send web-contents "event" (wsst/envelope-json msg))
  (wap/await! msg))

(defonce connected-clients* (atom #{}))

(defn handle-client-message [server msg]
  (js/console.log "NEW MSG"))

(defn start-ws! [server]
  (ws-server/start-ws!
    {::ws-server/port
     SERVER_PORT

     ::ws-server/on-client-connect
     (fn [{::ws-server/keys [client-id] :as env} message]
       (js/console.log "Client connect" client-id)

       (swap! connected-clients* conj client-id)

       (message-renderer! server
         {:com.wsscode.pathom.viz.electron.renderer.main/message-type
          :com.wsscode.pathom.viz.electron.renderer.main/connect-client

          ::ws-server/client-id
          client-id}))

     ::ws-server/on-client-disconnect
     (fn [{::ws-server/keys [client-id] :as env} client]
       (js/console.log "Client disconnect" client-id)

       (swap! connected-clients* disj client-id)

       (message-renderer! server
         {:com.wsscode.pathom.viz.electron.renderer.main/message-type
          :com.wsscode.pathom.viz.electron.renderer.main/disconnect-client

          ::ws-server/client-id
          client-id}))

     ::ws-server/on-client-message
     handle-client-message}))

(defn handle-renderer-message
  [server {::keys [type] :as message}]
  ;; LANDMARK: Hook up of incoming messages from Electron renderer
  (case type
    ::request-parser
    (go-promise
      (try
        (let [res (<? (ws-server/send-message! server
                        {:com.wsscode.pathom.viz.ws-connector.core/type
                         :com.wsscode.pathom.viz.ws-connector.core/parser-request

                         :edn-query-language.core/query
                         (:edn-query-language.core/query message)

                         ::ws-server/client-id
                         (::ws-server/client-id message)

                         ::wap/request-id
                         (random-uuid)}))]
          (message-renderer! server
            (wap/reply-message message res)))
        (catch :default e
          (js/console.error "Error handling response")
          (cljs.pprint/pprint (ex-data e)))))

    ::connected-parsers
    (message-renderer! server (wap/reply-message message @connected-clients*))))

(defonce started* (atom false))

(>defn start! [{::keys [web-contents] :as server}]
  [(s/keys :req [::web-contents]) => any?]
  (when-not @started*
    (reset! started* true)
    (let [server (merge server (start-ws! server))]
      (ipc-main/on-ipc-main-event
        #(handle-renderer-message server %2)))))
