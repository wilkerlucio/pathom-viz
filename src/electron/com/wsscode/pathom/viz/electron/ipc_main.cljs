(ns com.wsscode.pathom.viz.electron.ipc-main
  (:require
    ["electron" :refer [ipcMain]]
    [com.wsscode.async.processing :as wap]
    [com.wsscode.transit :as wsst]))

(defn on-ipc-main-event [f]
  (.on ipcMain "event"
    (wap/event-queue!
      (fn [evt msg]
        (if-let [msg' (wsst/unpack-json msg)]
          (if-not (wap/capture-response! msg')
            (f evt msg')))))))
