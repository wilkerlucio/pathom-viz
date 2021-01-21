(ns com.wsscode.pathom.viz.electron.background.main
  (:require
    ["electron" :as electron :refer [ipcMain]]
    ["path" :as path]
    ["url" :as url]
    [com.wsscode.pathom.viz.electron.background.server :as server]
    [com.wsscode.pathom.viz.transit :as wsst]))

(defonce web-contents* (atom nil))

(goog-define DEV false)

(defn create-window []
  (let [win          (electron/BrowserWindow. #js {:width          1200
                                                   :height         800
                                                   :webPreferences #js {:nodeIntegration true}})
        web-contents (.-webContents win)]
    (.loadURL win (url/format #js {:pathname (path/join js/__dirname ".." ".." "index.html")
                                   :protocol "file:"
                                   :slashes  "true"}))
    (if DEV (.openDevTools web-contents))
    (reset! web-contents* web-contents)
    (server/start! {::server/web-contents web-contents})))

(defn init []
  (electron/app.on "ready" create-window))
