(ns com.wsscode.pathom.viz.electron.renderer.main
  (:require [cljs.spec.alpha :as s]
            [cljs.reader :refer [read-string]]
            [clojure.core.async :as async :refer [go <! chan go-loop]]
            [com.wsscode.async.async-cljs :refer [go-promise <?]]
            [com.fulcrologic.fulcro-css.css-injection :as cssi]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.fulcrologic.fulcro.application :as fapp]
            [com.fulcrologic.fulcro.components :as fc]
            [com.fulcrologic.guardrails.core :refer [>def >defn >fdef => | <- ?]]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.viz.aux.demo-parser :as demo-parser]
            [com.wsscode.pathom.viz.client-parser :as cp]
            [com.wsscode.pathom.viz.helpers :as pvh]
            [com.wsscode.pathom.viz.parser-assistant :as assistant]
            [com.wsscode.pathom.viz.query-editor :as query.editor]
            [com.wsscode.pathom.viz.ui.kit :as ui]
            [goog.object :as gobj]
            [com.wsscode.pathom.viz.async-utils :as pv.async]
            [com.wsscode.transit :as wsst]))

(>def ::channel any?)

(defonce electron (js/require "electron"))
(defonce shell (gobj/get electron "shell"))

(defonce ipcRenderer (gobj/get electron "ipcRenderer"))

(defn after-load []
  (js/console.log "Done reloading"))

(defonce client-parsers
  (atom {::assistant/singleton demo-parser/parser}))

(defn use-electron-ipc [handler]
  (pvh/use-effect
    (fn use-electron-ipc-effect-up []
      (let [ch (async/chan (async/dropping-buffer 50000))]
        (.on ipcRenderer "event"
          (pv.async/event-queue! {::channel ch}
            (fn [e msg]
              (if-let [msg' (wsst/unpack-json msg)]
                (handler e msg')))))
        (js/console.log "Listening to ipc renderer events")

        (fn use-electron-ipc-effect-down []
          (async/close! ch))))
    []))

(def registry
  [cp/registry query.editor/registry])

(def parser
  (p/async-parser
    {::p/env     {::p/reader               [p/map-reader
                                            pc/reader3
                                            pc/open-ident-reader
                                            p/env-placeholder-reader]
                  ::cp/parsers*            client-parsers
                  ::p/placeholder-prefixes #{">"}}
     ::p/mutate  pc/mutate-async
     ::p/plugins [(pc/connect-plugin {::pc/register registry})
                  p/error-handler-plugin
                  p/elide-special-outputs-plugin
                  p/trace-plugin]}))

(defonce response-notifiers* (atom {}))

(defn message-background! [msg]
  (.send ipcRenderer "event" (wsst/envelope-json msg))
  (pv.async/await! msg))

(>defn request-client-parser!
  [{:edn-query-language.core/keys    [query]
    :com.wsscode.node-ws-server/keys [client-id]
    :as                              msg}]
  [(s/keys :req [:com.wsscode.node-ws-server/client-id
                 :edn-query-language.core/query])
   => ::channel]
  (message-background! {:com.wsscode.node-ws-server/client-id client-id
                        :edn-query-language.core/query        query
                        ::pv.async/response-id                (random-uuid)}))

(>def ::message-type qualified-keyword?)

(defn electron-message-handler [root {::keys                           [message-type]
                                      :com.wsscode.node-ws-server/keys [client-id]
                                      :as                              msg}]
  (let [multi-parser-ref (:ui/multi-parser (fc/component->state-map root))]
    (case message-type
      ::connect-client
      (do
        (swap! client-parsers assoc client-id
          (fn [_ tx]
            (go-promise
              (try
                (js/console.log "SENDING msg")
                (<? (request-client-parser!
                      {:edn-query-language.core/query        tx
                       :com.wsscode.node-ws-server/client-id client-id}))
                (catch :default e
                  (js/console.error "response failed" e))))
            (js/console.log "call to the parser with" msg)
            {}))

        (assistant/reload-available-parsers root multi-parser-ref))

      ::disconnect-client
      (js/console.log "Disconnect client")

      (js/console.warn "Unknown message received" msg))))

(defonce app
  (fapp/fulcro-app
    {:remotes
     {:remote
      (pvh/pathom-remote #(parser (assoc % ::cp/parsers @client-parsers) %2))}}))

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

(defn init []
  (fapp/mount! app Root "app-root")
  (cssi/upsert-css "pathom-viz" {:component Root}))

(init)
