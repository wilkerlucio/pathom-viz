(ns com.wsscode.node-ws-server
  (:require
    [cljs.nodejs :as nodejs]
    [cljs.spec.alpha :as s]
    [com.fulcrologic.guardrails.core :refer [>def >defn >fdef => | <- ?]]
    [com.wsscode.async.async-cljs :refer [go <!]]
    [com.wsscode.transit :as wsst]
    [taoensso.sente.packers.transit :as st]
    [taoensso.sente.server-adapters.express :as sente-express]
    [taoensso.timbre :as log]))

(>def ::port pos-int?)

(>def ::on-client-connect fn?)
(>def ::on-client-disconnect fn?)
(>def ::on-client-message fn?)

(defonce channel-socket-server (atom nil))
(defonce server-atom (atom nil))

(defn make-packer
  "Returns a json packer for use with sente."
  [{:keys [read write]}]
  (st/->TransitPacker :json
    {:handlers (merge {"default" (wsst/->DefaultHandler)} write)}
    {:handlers (or read {})}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Express Boilerplate Plumbing
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def http (nodejs/require "http"))
(def express (nodejs/require "express"))
(def express-ws (nodejs/require "express-ws"))
(def ws (nodejs/require "ws"))
(def cors (nodejs/require "cors"))
(def body-parser (nodejs/require "body-parser"))

(defn routes [express-app {:keys [ajax-post-fn ajax-get-or-ws-handshake-fn]}]
  (doto express-app
    (.use (cors))
    (.ws "/chsk"
      (fn [ws req next]
        (ajax-get-or-ws-handshake-fn req nil nil
          {:websocket? true
           :websocket  ws})))
    (.get "/chsk" ajax-get-or-ws-handshake-fn)
    (.post "/chsk" ajax-post-fn)
    (.use (fn [req res next]
            (log/warn "Unhandled request: %s" (.-originalUrl req))
            (next)))))

(defn wrap-defaults [express-app routes ch-server]
  (doto express-app
    (.use (fn [req _res next]
            (log/trace "Request: %s" (.-originalUrl req))
            (next)))
    (.use (.urlencoded body-parser #js {:extended false}))
    (routes ch-server)))

(defn start-web-server! [{::keys [port]}]
  (log/info "Starting express")
  (let [express-app       (express)
        express-ws-server (express-ws express-app)]
    (wrap-defaults express-app routes @channel-socket-server)
    (let [http-server (.listen express-app port)]
      (reset! server-atom
        {:express-app express-app
         :ws-server   express-ws-server
         :http-server http-server
         :stop-fn     #(.close http-server)
         :port        port}))))

;; API

(>defn send-message! [{::keys [send-fn client-id] :as env} msg]
  [map? vector? => any?]
  (send-fn client-id msg))

(>defn start-ws!
  [{::keys [port on-client-connect
            on-client-disconnect
            on-client-message]
    :as    config}]
  [(s/keys :req [::port])
   => any?]
  (when-not @channel-socket-server
    (reset! channel-socket-server
      (sente-express/make-express-channel-socket-server!
        {:packer        (make-packer {})
         :csrf-token-fn nil
         :user-id-fn    :client-id})))
  (go
    (loop []
      (when-some [{:keys [client-id event] :as message} (<! (:ch-recv @channel-socket-server))]
        (let [[event-type event-data] event
              config' (assoc config ::socket-conn @channel-socket-server
                                    ::client-id client-id
                                    ::send-fn (:send-fn @channel-socket-server))]
          (case event-type
            :chsk/uidport-open
            (on-client-connect config' message)
            :chsk/uidport-close
            (on-client-disconnect config' message)
            :chsk/ws-ping
            (js/console.log "ws-ping from client:" client-id)

            ; else
            (on-client-message config' message))))
      (recur)))

  (log/info "Websocket Server Listening on port" port)
  (reset! server-atom (start-web-server! config)))
