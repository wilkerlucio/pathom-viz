(ns com.wsscode.pathom.viz.electron.react.use-electron-ipc
  (:require [cljs.core.async :as async]
            [com.wsscode.async.processing :as wap]
            [com.wsscode.pathom.viz.helpers :as pvh]
            [com.wsscode.transit :as wsst]
            [goog.object :as gobj]))

(defonce electron (js/require "electron"))
(defonce ipcRenderer (gobj/get electron "ipcRenderer"))

(defn use-electron-ipc [handler]
  (pvh/use-effect
    (fn use-electron-ipc-effect-up []
      (let [ch (async/chan (async/dropping-buffer 50000))]
        (.on ipcRenderer "event"
          (wap/event-queue! {::channel ch}
            (fn [e msg]
              (if-let [msg' (wsst/unpack-json msg)]
                (if-not (wap/capture-response! msg')
                  (handler e msg'))))))
        (js/console.log "Listening to ipc renderer events")

        (fn use-electron-ipc-effect-down []
          (async/close! ch))))
    []))
