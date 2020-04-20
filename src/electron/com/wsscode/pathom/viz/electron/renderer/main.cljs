(ns com.wsscode.pathom.viz.electron.renderer.main
  "Remote helpers to call remote parsers from the client."
  (:require [cljs.reader :refer [read-string]]
            [cljs.spec.alpha :as s]
            [clojure.core.async :as async :refer [go <! chan go-loop]]
            [clojure.set :as set]
            [com.fulcrologic.fulcro-css.css-injection :as cssi]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.fulcrologic.fulcro.algorithms.merge :as merge]
            [com.fulcrologic.fulcro.application :as fapp]
            [com.fulcrologic.fulcro.components :as fc]
            [com.fulcrologic.guardrails.core :refer [>def >defn >fdef => | <- ?]]
            [com.wsscode.async.async-cljs :refer [go-promise <?]]
            [com.wsscode.async.processing :as wap]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.viz.electron.react.use-electron-ipc :refer [use-electron-ipc]]
            [com.wsscode.pathom.viz.helpers :as pvh]
            [com.wsscode.pathom.viz.local-parser :as local.parser]
            [com.wsscode.pathom.viz.parser-assistant :as assistant]
            [com.wsscode.pathom.viz.request-history :as request-history]
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
  (wap/await! msg))

(defn create-background-parser [client-id]
  (fn [_ tx]
    (go-promise
      (try
        (<? (message-background!
              {:com.wsscode.pathom.viz.electron.background.server/type
               :com.wsscode.pathom.viz.electron.background.server/request-parser

               :edn-query-language.core/query
               tx

               :com.wsscode.node-ws-server/client-id
               client-id

               ::wap/request-id
               (random-uuid)}))
        (catch :default e
          (js/console.error "response failed" e))))))

(defn add-background-parser! [this client-id]
  (swap! local.parser/client-parsers assoc client-id (create-background-parser client-id))
  (assistant/initialize-assistant this client-id))

(defn multi-parser-ref [this]
  (:ui/multi-parser (fc/component->state-map this)))

(defn reload-parsers-ui! [this]
  (assistant/reload-available-parsers this (multi-parser-ref this)))

(defn electron-message-handler
  [this {::keys                           [message-type]
         :com.wsscode.node-ws-server/keys [client-id]
         :as                              msg}]
  (case message-type
    ::connect-client
    (do
      (add-background-parser! this client-id)
      (reload-parsers-ui! this))

    ::disconnect-client
    (js/console.log "Disconnect client")

    ::pathom-request
    (let [{:com.wsscode.pathom.viz.ws-connector.core/keys [request-id tx]} msg]
      (merge/merge-component! this request-history/RequestView
        {::request-history/request-id request-id
         ::request-history/request    tx}
        :append [::request-history/id client-id ::request-history/requests]))

    ::pathom-request-done
    (let [{:com.wsscode.pathom.viz.ws-connector.core/keys [request-id response]} msg]
      (merge/merge-component! this request-history/RequestView
        {::request-history/request-id request-id
         ::request-history/response   response}))

    (js/console.warn "Unknown message received" msg)))

(defn sync-background-parsers [this]
  (go-promise
    (try
      (let [background-parsers (<? (message-background!
                                     {:com.wsscode.pathom.viz.electron.background.server/type
                                      :com.wsscode.pathom.viz.electron.background.server/connected-parsers

                                      ::wap/request-id
                                      (wap/random-request-id)}))
            local-parsers      (set (keys @local.parser/client-parsers))
            missing            (set/difference background-parsers local-parsers)
            ;remove             (set/difference local-parsers background-parsers)
            ]
        (doseq [client-id missing]
          (add-background-parser! this client-id))

        #_
        (fc/transact! this
          (into []
                (map (fn [cid]
                       `(assistant/remove-parser {:com.wsscode.pathom.viz.client-parser/parser-id cid})))
                remove)
          {:ref (multi-parser-ref this)})

        (reload-parsers-ui! this))
      (catch :default e
        (js/console.error "Error syncing background parsers" e))))
  js/undefined)

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
  (pvh/use-effect #(sync-background-parsers this) [])
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

(pc/defmutation open-external [_ {:keys [url]}]
  {::pc/sym    'com.wsscode.pathom.viz.ui.mutation-effects/open-external
   ::pc/params [:url]}
  (.openExternal shell url))

(defonce app
  (fapp/fulcro-app
    {:remotes
     {:remote
      (pvh/pathom-remote (local.parser/parser [open-external]))}}))

(defn init []
  (fapp/mount! app Root "app-root")
  (cssi/upsert-css "pathom-viz" {:component Root}))

(init)
