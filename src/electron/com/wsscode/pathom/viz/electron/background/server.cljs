(ns com.wsscode.pathom.viz.electron.background.server
  (:require
    [cljs.nodejs :as nodejs]
    ["electron" :refer [ipcMain]]
    ["socket.io" :as Server]
    [goog.object :as gobj]
    [fulcro.inspect.remote.transit :as encode]))

(goog-define SERVER_PORT 8237)
(defonce the-client (atom nil))

(defn process-client-message [web-contents msg reply-fn]
  (try
    (when web-contents
      (.send web-contents "event" #js {"fulcro-inspect-remote-message" msg}))
    (catch :default e
      (js/console.error e))))

(defn start! [{:keys [content-atom]}]
  (let [io (Server)]
    (.on ipcMain "event" (fn [evt arg]
                           (when @the-client
                             (.emit @the-client "event" arg))))
    (.on io "connection" (fn [client]
                           (reset! the-client client)
                           (.on client "event"
                             (fn [data reply-fn]
                               (when-let [web-contents (some-> content-atom deref)]
                                 (process-client-message web-contents data reply-fn))))))
    (.listen io SERVER_PORT)))


