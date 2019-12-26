(ns com.wsscode.socket-io.client-connectors.io-socket
  (:require [com.wsscode.socket-io.client :as sio-client]
            [com.wsscode.transit :as t])
  (:import (io.socket.client IO Socket)
           (io.socket.emitter Emitter$Listener)
           (org.json JSONObject)))

(deftype Listener [callback]
  Emitter$Listener
  (call [& args]
    (apply callback args)))

(defn listener [callback]
  (->Listener callback))

(defn event-connect [config]
  (println "CONNECT" config))

(defn event-message [{::sio-client/keys [on-client-message] :as connector} ^JSONObject message]
  (try
    (when-let [transit-msg (.getString message sio-client/transfer-key)]
      (println "EVENT" message)
      (if on-client-message
        (on-client-message connector (t/read transit-msg))))
    (catch Throwable e
      (println "event missing transit message"))))

(defn connect [{::sio-client/keys [server-url] :as connector}]
  (println "CONNECT TO" server-url)
  (let [socket (doto (IO/socket ^String server-url)
                 (.on Socket/EVENT_CONNECT
                   (listener #(event-connect connector)))

                 (.on "event"
                   (listener #(event-message connector %)))

                 (.connect))]
    (assoc connector ::socket socket)))

(defn send-message [{::keys [^Socket socket]} msg]
  (.emit socket "event"
    (doto (JSONObject.)
      (.put sio-client/transfer-key (t/write msg)))))

(defn socket-io-connector
  ([] (socket-io-connector {}))
  ([config]
   (merge
     config
     {::sio-client/connect
      connect

      ::sio-client/send-message
      send-message})))
