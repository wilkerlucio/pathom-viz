(ns com.wsscode.pathom.viz.electron.ipc-main
  (:require
    ["electron" :refer [ipcMain]]
    [com.wsscode.transit :as wsst]
    [com.wsscode.pathom.viz.async-utils :as pv.async]))

(defn on-ipc-main-event [f]
  (.on ipcMain "event"
    (pv.async/event-queue!
      (fn [evt msg]
        (if-let [msg' (wsst/unpack-json msg)]
          (f evt msg'))))))
