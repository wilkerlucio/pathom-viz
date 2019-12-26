(ns com.wsscode.electron.ipc-main
  (:require
    ["electron" :refer [ipcMain]]))

(defn on-ipc-main-event [f]
  (.on ipcMain "event"
    (fn [evt arg]
      (f evt arg))))
