(ns com.wsscode.pathom.viz.electron.background.main
  (:require
    [com.wsscode.pathom.viz.electron.background.server :as server]
    ["electron" :as electron]
    ["path" :as path]
    ["url" :as url]))

(defn create-window []
  (let [win (electron/BrowserWindow. #js {:width          800
                                          :height         600
                                          :webPreferences #js {:nodeIntegration true}})]
    (.loadURL win (url/format #js {:pathname (path/join js/__dirname ".." ".." "index.html")
                                   :protocol "file:"
                                   :slashes  "true"}))
    (.. win -webContents openDevTools)))

(defn init []
  (js/console.log "start")
  (electron/app.on "ready" create-window)
  (server/start! {}))

(defn after-load []
  (js/console.log "Done reloading"))
