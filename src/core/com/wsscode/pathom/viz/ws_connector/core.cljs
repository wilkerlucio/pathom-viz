(ns com.wsscode.pathom.viz.ws-connector.core
  (:require
    [cljs.core.async :as async :refer [>! <! go go-loop put!]]
    [clojure.pprint :refer [pprint]]
    [com.fulcrologic.guardrails.core :refer [>def >defn >fdef => | <- ?]]
    [com.wsscode.async.async-cljs :refer [let-chan]]
    [com.wsscode.async.processing :as wap]
    [com.wsscode.transit :as wsst]
    [taoensso.encore :as enc]
    [taoensso.sente :as sente]
    [taoensso.sente.packers.transit :as st]))

(>def ::host string?)
(>def ::port pos-int?)
(>def ::path string?)
(>def ::on-connect fn?)
(>def ::on-disconnect fn?)
(>def ::on-message fn?)
(>def ::parser-id any?)

(defn make-packer
  "Returns a json packer for use with sente."
  [{:keys [read write]}]
  (st/->TransitPacker :json
    {:handlers (merge {"default" (wsst/->DefaultHandler)} write)}
    {:handlers (or read {})}))

(goog-define DEFAULT_HOST "localhost")
(goog-define DEFAULT_PORT 8240)

(def backoff-ms #(enc/exp-backoff % {:max 1000}))

(defn start-ws-messaging!
  [{::keys [host path port on-connect on-disconnect on-message
            send-ch parser-id]}]
  (let [client-id
        (str (or parser-id (random-uuid)))

        sente-socket-client
        (sente/make-channel-socket-client! (or path "/chsk") "no-token-desired"
          {:type           :auto
           :host           (or host DEFAULT_HOST)
           :port           (or port DEFAULT_PORT)
           :protocol       :http
           :packer         (make-packer {})
           :client-uuid    client-id
           :wrap-recv-evs? false
           :backoff-ms-fn  backoff-ms})]

    ; processing for queue to send data to the server
    (let [{:keys [state send-fn]} sente-socket-client]
      (go-loop [attempt 1]
        (let [open? (:open? @state)]
          (if open?
            (when-let [msg (<! send-ch)]
              (js/console.log "SEND" msg)
              (send-fn [::message (assoc msg :com.wsscode.node-ws-server/client-id client-id)]))
            (do
              (js/console.log (str "Waiting for channel to be ready"))
              (async/<! (async/timeout (backoff-ms attempt)))))
          (recur (if open? 1 (inc attempt))))))

    ; processing messages from the server
    (let [{:keys [state ch-recv]} sente-socket-client]
      (go-loop [attempt 1]
        (let [open? (:open? @state)]
          (if open?
            (do (let [{:keys [id ?data] :as evt} (<! ch-recv)]
                  (js/console.log "MSG RECEIVED" (dissoc evt :ch-recv))
                  (if (= id :com.wsscode.node-ws-server/message)
                    (on-message {} ?data))))
            (do
              (js/console.log (str "Waiting for channel to be ready"))
              (async/<! (async/timeout (backoff-ms attempt)))))
          (recur (if open? 1 (inc attempt))))))))

(defn connect-ws!
  [config]
  (js/console.log "Connecting to websocket" config)
  (start-ws-messaging! config))

;;;;

(defn send-message! [send-ch msg]
  (put! send-ch msg))

(defn handle-pathom-viz-message
  [{::keys [parser send-ch]}
   {::keys                        [type]
    :edn-query-language.core/keys [query]
    :as                           msg}]
  (case type
    ::parser-request
    (let-chan [res (parser {} query)]
      (send-message! send-ch (wap/reply-message msg res)))

    (js/console.warn "Unknown message received" msg)))

(defn connect-parser [config parser]
  (let [send-ch (async/chan (async/dropping-buffer 50000))
        config' (assoc config ::parser parser ::send-ch send-ch)]
    (connect-ws!
      (merge
        {::on-message
         (fn [_ msg]
           (handle-pathom-viz-message config' msg))

         ::send-ch
         send-ch}
        config))
    (fn [env tx]
      (let [id (random-uuid)]
        (send-message! send-ch {::type       ::pathom-request
                                ::request-id id
                                ::tx         tx})
        (let-chan [res (parser env tx)]
          (send-message! send-ch {::type       ::pathom-request-done
                                  ::request-id id
                                  ::response   tx})
          res)))))
