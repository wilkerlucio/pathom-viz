(ns com.wsscode.pathom.viz.electron.renderer.main
  "Remote helpers to call remote parsers from the client."
  (:require [cljs.reader :refer [read-string]]
            [cljs.spec.alpha :as s]
            [clojure.core.async :as async :refer [go <! chan go-loop]]
            [com.fulcrologic.fulcro-css.css-injection :as cssi]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.fulcrologic.fulcro.application :as fapp]
            [com.fulcrologic.fulcro.components :as fc]
            [com.fulcrologic.guardrails.core :refer [>def >defn >fdef => | <- ?]]
            [com.wsscode.async.async-cljs :refer [go-promise <?]]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.viz.async-utils :as pv.async]
            [com.wsscode.pathom.viz.electron.react.use-electron-ipc :refer [use-electron-ipc]]
            [com.wsscode.pathom.viz.helpers :as pvh]
            [com.wsscode.pathom.viz.local-parser :as local.parser]
            [com.wsscode.pathom.viz.parser-assistant :as assistant]
            [com.wsscode.pathom.viz.ui.kit :as ui]
            [com.wsscode.transit :as wsst]
            [goog.object :as gobj]))

(>def ::channel any?)
(>def ::message-type qualified-keyword?)

(defonce electron (js/require "electron"))
(defonce shell (gobj/get electron "shell"))

(defonce ipcRenderer (gobj/get electron "ipcRenderer"))

(defn message-background! [msg]
  (.send ipcRenderer "event" (wsst/envelope-json msg))
  (pv.async/await! msg))

(defn electron-message-handler [root {::keys                           [message-type]
                                      :com.wsscode.node-ws-server/keys [client-id]
                                      :as                              msg}]
  (let [multi-parser-ref (:ui/multi-parser (fc/component->state-map root))]
    (case message-type
      ::connect-client
      (do
        (swap! local.parser/client-parsers assoc client-id
          (fn [_ tx]
            (go-promise
              (try
                (<? (message-background!
                      {:edn-query-language.core/query        tx
                       :com.wsscode.node-ws-server/client-id client-id
                       ::pv.async/request-id                 (random-uuid)}))
                (catch :default e
                  (js/console.error "response failed" e))))))

        (assistant/reload-available-parsers root multi-parser-ref))

      ::disconnect-client
      (js/console.log "Disconnect client")

      (js/console.warn "Unknown message received" msg))))

;; App Root Container

(fc/defsc Root
  [this {:ui/keys [multi-parser]}]
  {:pre-merge  (fn [{:keys [current-normalized data-tree]}]
                 (merge {:ui/multi-parser {}} current-normalized data-tree))
   :query      [{:ui/multi-parser (fc/get-query assistant/MultiParserManager)}]
   :css        [[:body {:margin "0"}]
                [:#app-root {:width      "100vw"
                             :height     "100vh"
                             :box-sizing "border-box"
                             ;:padding    "10px"
                             :overflow   "hidden"
                             :display    "flex"}]
                [:.footer {:background "#eee"
                           :display    "flex"
                           :padding    "6px 10px"
                           :text-align "right"}
                 ui/text-sans-13
                 [:a {:text-decoration "none"}]]]
   :use-hooks? true}
  (use-electron-ipc #(electron-message-handler this %2))
  (ui/column (ui/gc :.flex)
    (assistant/multi-parser-manager multi-parser)
    (dom/div :.footer
      (dom/a {:href    "#"
              :onClick (ui/prevent-default #(.openExternal shell "https://github.com/wilkerlucio/pathom-viz"))}
        "Pathom Viz")
      (dom/div (ui/gc :.flex))
      (dom/div "Freely distributed by "
        (dom/a {:href    "#"
                :onClick (ui/prevent-default #(.openExternal shell "https://github.com/wilkerlucio"))}
          "Wilker Lucio")))))

(def root (fc/factory Root {:keyfn ::id}))

;; App Init

(defonce app
  (fapp/fulcro-app
    {:remotes
     {:remote
      (pvh/pathom-remote local.parser/parser)}}))

(defn init []
  (fapp/mount! app Root "app-root")
  (cssi/upsert-css "pathom-viz" {:component Root}))

(init)

;; After Load

(defn after-load []
  (js/console.log "Done reloading"))
