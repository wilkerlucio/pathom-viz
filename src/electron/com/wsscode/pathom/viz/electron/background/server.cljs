(ns com.wsscode.pathom.viz.electron.background.server
  (:require
    [com.wsscode.pathom.viz.electron.ipc-main :as ipc-main]
    [com.wsscode.node-ws-server :as ws-server]
    [com.fulcrologic.guardrails.core :refer [>def >defn >fdef => | <- ?]]
    [com.wsscode.transit :as wsst]
    [cljs.spec.alpha :as s]
    [com.wsscode.async.async-cljs :refer [go-promise <?]]
    [com.wsscode.pathom.viz.async-utils :as pv.async]))

(goog-define SERVER_PORT 8240)

(>def ::web-contents
  "The window reference for the Electron application renderer instance"
  any?)

(>defn message-renderer!
  [{::keys [web-contents]} msg]
  [(s/keys :req [::web-contents]) any?
   => any?]
  (.send web-contents "event" (wsst/envelope-json msg))
  (pv.async/await! msg))

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
       #_(ws-server/send-message! env
           {::ws-server/message-type :hello/dear
            ::ws-server/message-data "Hello Dear"
            ::pv.async/request-id    (random-uuid)}))

     ::ws-server/on-client-disconnect
     (fn [_ client]
       (js/console.log "get out"))

     ::ws-server/on-client-message
     (fn [_ msg]
       (js/console.log "NEW MSG")
       #_ (cljs.pprint/pprint msg))}))

(defn handle-renderer-message
  [server message]
  ;; LANDMARK: Hook up of incoming messages from Electron renderer
  (cljs.pprint/pprint ["RENDER MSG" server message])
  (cond
    (:edn-query-language.core/query message)
    (go-promise
      (js/console.log "requesting data from client parser")
      (cljs.pprint/pprint server)
      (try
        (let [res (<? (ws-server/send-message! server
                        {:com.wsscode.pathom.viz.ws-connector.core/type
                         :com.wsscode.pathom.viz.ws-connector.core/parser-request

                         :edn-query-language.core/query
                         (:edn-query-language.core/query message)

                         ::ws-server/client-id
                         (::ws-server/client-id message)

                         ::pv.async/request-id
                         (random-uuid)}))]
          (js/console.log "sending message back")
          (message-renderer! server
            (pv.async/reply-message message res)))
        (catch :default e
          (js/console.error "Error handling response")
          (cljs.pprint/pprint (ex-data e)))))))

(defonce started* (atom false))

(>defn start! [{::keys [web-contents] :as server}]
  [(s/keys :req [::web-contents]) => any?]
  (when-not @started*
    (reset! started* true)

    (.on web-contents "dom-ready"
      (fn []
        (let [server (merge server (start-ws! server))]
          (ipc-main/on-ipc-main-event
            #(handle-renderer-message server %2)))))))
