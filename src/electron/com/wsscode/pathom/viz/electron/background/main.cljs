(ns com.wsscode.pathom.viz.electron.background.main
  (:require
    ["electron" :as electron]
    ["path" :as path]
    ["url" :as url]
    ["electron-updater" :refer [autoUpdater]]
    [com.wsscode.pathom.viz.electron.background.server :as server]))

(defonce web-contents* (atom nil))

(goog-define DEV false)

(defn create-window []
  (let [win          (electron/BrowserWindow. #js {:width          1200
                                                   :height         800
                                                   :webPreferences #js {:nodeIntegration            true
                                                                        :contextIsolation           false
                                                                        :enableRemoteModule         true
                                                                        :sandbox                    false
                                                                        :nodeIntegrationInSubFrames true
                                                                        :nodeIntegrationInWorker    true
                                                                        :webviewTag                 true}})
        web-contents (.-webContents win)]
    (.loadURL win (url/format #js {:pathname (path/join js/__dirname ".." ".." "index.html")
                                   :protocol "file:"
                                   :slashes  "true"}))
    (if DEV (.openDevTools web-contents))
    (reset! web-contents* web-contents)
    (server/start! {::server/web-contents web-contents})))

(defn init []
  (.checkForUpdatesAndNotify autoUpdater)
  (electron/app.on "ready" create-window))
