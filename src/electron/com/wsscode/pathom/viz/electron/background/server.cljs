(ns com.wsscode.pathom.viz.electron.background.server
  (:require
    [cljs.spec.alpha :as s]
    [com.fulcrologic.guardrails.core :refer [>def >defn >fdef => | <- ?]]
    [com.wsscode.async.async-cljs :refer [go-promise <? <!p]]
    [com.wsscode.async.processing :as wap]
    [com.wsscode.node-ws-server :as ws-server]
    [com.wsscode.pathom.viz.electron.ipc-main :as ipc-main]
    [com.wsscode.transit :as wsst]
    ["node-fetch" :default fetch]
    [clojure.set :as set]))

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
(defonce http-clients* (atom {}))

(defn send-http-request! [{::ws-server/keys [client-id]
                           :as              msg}]
  (if-let [http-url (get @http-clients* client-id)]
    (do
      (fetch http-url #js {:method  "POST"
                           :headers #js {"Content-Type" "application/transit+json"}
                           :body    (wsst/write msg)})
      (wap/await! msg))
    (js/console.warn "Tried to send http message but client-id doesn't have url")))

(defn handle-client-connect
  [server
   {::ws-server/keys                                        [client-id]
    :com.wsscode.pathom.viz.ws-connector.impl.http-clj/keys [local-http-address]}]
  (if local-http-address (swap! http-clients* assoc client-id local-http-address))
  (when-not (contains? @connected-clients* client-id)
    (js/console.log "Client connect" client-id)

    (swap! connected-clients* conj client-id)

    (message-renderer! server
      {:com.wsscode.pathom.viz.electron.renderer.main/message-type
       :com.wsscode.pathom.viz.electron.renderer.main/connect-client

       ::ws-server/client-id
       client-id})))

(defn handle-client-disconnect [server {::ws-server/keys [client-id]}]
  (js/console.log "Client disconnect" client-id)

  (swap! connected-clients* disj client-id)

  (message-renderer! server
    {:com.wsscode.pathom.viz.electron.renderer.main/message-type
     :com.wsscode.pathom.viz.electron.renderer.main/disconnect-client

     ::ws-server/client-id
     client-id}))

(defn handle-client-message [server {::ws-server/keys [client-id]} msg]
  (let [type (:com.wsscode.pathom.viz.ws-connector.core/type msg)]
    (case type
      :com.wsscode.pathom.viz.ws-connector.core/ping
      nil

      :com.wsscode.pathom.viz.ws-connector.core/pathom-request
      (message-renderer! server
        (assoc msg
          :com.wsscode.pathom.viz.electron.renderer.main/message-type
          :com.wsscode.pathom.viz.electron.renderer.main/pathom-request
          ::ws-server/client-id client-id))

      :com.wsscode.pathom.viz.ws-connector.core/pathom-request-done
      (message-renderer! server
        (assoc msg
          :com.wsscode.pathom.viz.electron.renderer.main/message-type
          :com.wsscode.pathom.viz.electron.renderer.main/pathom-request-done
          ::ws-server/client-id client-id))

      :com.wsscode.pathom.viz.ws-connector.core/log-entry
      (message-renderer! server
        (assoc msg
          :com.wsscode.pathom.viz.electron.renderer.main/message-type
          :com.wsscode.pathom.viz.electron.renderer.main/log-entry))

      (js/console.warn "WS client sent unknown message" (pr-str msg)))))

(>defn handle-http-request [server msg]
  [map? (s/keys :req [::ws-server/client-id])
   => any?]
  (handle-client-connect server msg)
  (handle-client-message server msg msg))

(defn start-ws! [server]
  (ws-server/start-ws!
    {::ws-server/port
     SERVER_PORT

     ::ws-server/on-client-connect
     (fn [client _message]
       (handle-client-connect server client))

     ::ws-server/on-client-disconnect
     (fn [client _message]
       (handle-client-disconnect server client))

     ::ws-server/on-http-request
     #(handle-http-request server %2)

     ::ws-server/on-client-message
     #(handle-client-message server % %3)}))

(defn send-message! [server {::ws-server/keys [client-id] :as msg}]
  (if (get @http-clients* client-id)
    (send-http-request! msg)
    (ws-server/send-message! server msg)))

(defn handle-renderer-message
  [server {::keys [type] :as message}]
  ;; LANDMARK: Hook up of incoming messages from Electron renderer
  (case type
    ::request-parser
    (go-promise
      (try
        (let [res (<? (send-message! server
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
