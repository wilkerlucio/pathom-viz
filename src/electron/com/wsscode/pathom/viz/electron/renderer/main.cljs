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
            [com.fulcrologic.fulcro.mutations :as fm]
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
            [com.wsscode.pathom.viz.timeline :as timeline]
            [com.wsscode.pathom.viz.trace :as trace]
            [com.wsscode.pathom.viz.worker :as pvw]
            [com.wsscode.pathom.viz.transit :as wsst]
            [com.wsscode.pathom.viz.fulcro]
            [com.wsscode.pathom.viz.ui.kit :as ui]
            [com.wsscode.pathom3.viz.plan :as viz-plan]
            [goog.object :as gobj]
            [helix.core :as h]
            [com.wsscode.pathom.viz.trace-with-plan :as trace+plan]))

(>def ::channel any?)
(>def ::message-type qualified-keyword?)

(defonce ^js electron (js/require "electron"))
(defonce ^js shell (gobj/get electron "shell"))

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
               (random-uuid)

               ::wap/timeout
               180000}))
        (catch :default e
          (js/console.error "response failed" e))))))

(defn add-background-parser! [this client-id]
  (swap! local.parser/client-parsers assoc client-id (create-background-parser client-id))
  (assistant/initialize-assistant this client-id))

(defn multi-parser-ref [this]
  (-> (fc/component->state-map this) :comp/ident :comp/connections-and-logs :ui/multi-parser))

(defn reload-parsers-ui! [this]
  (assistant/reload-available-parsers this (multi-parser-ref this)))

(fm/defmutation log-new-entry [{:keys [entry]}]
  (action [{:keys [state]}]
    (let [now (js/Date.)]
      (swap! state update-in [:comp/ident :comp/logs-view ::logs]
        assoc now entry)
      (if (meta entry)
        (swap! state update-in [:comp/ident :comp/logs-view ::logs-meta]
          assoc now (meta entry)))
      (swap! state update-in [:comp/ident :comp/logs-view]
        assoc ::log-current-value now)
      (swap! state update-in [:comp/ident :comp/connections-and-logs]
        assoc :ui/current-tab ::tab-logs))))

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
    (let [{:com.wsscode.pathom.viz.ws-connector.core/keys [request-id tx] :as req} msg]
      (merge/merge-component! this request-history/RequestView
        {::request-history/request-id request-id
         ::request-history/request    tx}
        :append [::request-history/id client-id ::request-history/requests]))

    ::pathom-request-done
    (let [{:com.wsscode.pathom.viz.ws-connector.core/keys [request-id response]} msg]
      (merge/merge-component! this request-history/RequestView
        {::request-history/request-id request-id
         ::request-history/response   response}))

    ::log-entry
    (fc/transact! this [(log-new-entry {:entry (:com.wsscode.pathom.viz.ws-connector.core/entry msg)})])

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

        #_(fc/transact! this
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

(fm/defmutation clear-logs [_]
  (action [{:keys [state]}]
    (swap! state assoc-in [:comp/ident :comp/logs-view ::logs] nil)
    (swap! state assoc-in [:comp/ident :comp/logs-view ::log-current-value] nil)))

(fc/defsc LogsView
  [this {::keys [logs logs-meta log-current-value]}]
  {:ident      (fn [_] [:comp/ident :comp/logs-view])
   :query      [::logs-view-id ::logs ::logs-meta ::log-current-value]
   :use-hooks? true}
  (let [ds      (pvh/use-persistent-state ::viz-plan/display-type ::viz-plan/display-type-label)
        log-val (with-meta (get logs log-current-value) (get logs-meta log-current-value))]
    (ui/row {:style {:flex "1" :overflow "hidden"}}
      (ui/column {:style {:alignSelf "stretch"}}
        (ui/button {:onClick #(fc/transact! this [(clear-logs {})])} "Clear logs")
        (ui/dom-select {:value    log-current-value
                        :onChange #(fm/set-value! this ::log-current-value %2)
                        :size     "3"
                        :style    {:flex    "1"
                                   :outline "none"}}
          (for [t (sort (keys logs))]
            (ui/dom-option {:key (.getTime t) :value t}
              (.toLocaleTimeString t)))))
      (ui/column {:style {:flex "1" :overflow "hidden"}}
        (if log-val
          (case (:pathom.viz.log/type log-val)
            :pathom.viz.log.type/plan-and-stats
            (fc/fragment
              (ui/section-header {}
                (ui/row {:classes [:.center]}
                  (dom/div (ui/gc :.flex) "Graph Viz")
                  (ui/dom-select {:value    @ds
                                  :onChange #(reset! ds %2)}
                    (ui/dom-option {:value ::viz-plan/display-type-label} "Display: resolver name")
                    (ui/dom-option {:value ::viz-plan/display-type-node-id} "Display: node id"))))
              (ui/column {:style {:flex "1"}}
                (h/$ viz-plan/PlanGraphView
                  {:run-stats    log-val
                   :display-type @ds})))

            :pathom.viz.log.type/trace
            (fc/fragment
              (ui/section-header {} "Trace")
              (trace+plan/trace-with-plan
                {:com.wsscode.pathom/trace (pvh/response-trace log-val)}))

            (pr-str log-val)))))))

(def logs-view (fc/factory LogsView))

(fc/defsc ConnectionsAndLogs
  [this {:ui/keys [multi-parser logs-view-data current-tab]}]
  {:pre-merge  (fn [{:keys [current-normalized data-tree]}]
                 (merge {:ui/multi-parser   {}
                         :ui/logs-view-data {}} current-normalized data-tree))
   :ident      (fn [_] [:comp/ident :comp/connections-and-logs])
   :query      [{:ui/multi-parser (fc/get-query assistant/MultiParserManager)}
                {:ui/logs-view-data (fc/get-query LogsView)}
                :ui/current-tab]
   :use-hooks? true}
  (use-electron-ipc #(electron-message-handler this %2))
  (pvh/use-effect #(sync-background-parsers this) [])
  (let [current-tab (or current-tab ::tab-connections)]
    (ui/tab-container {}
      (ui/tab-nav {::ui/active-tab-id current-tab}
        [[{::ui/tab-id ::tab-connections
           :onClick    #(fm/set-value! this :ui/current-tab ::tab-connections)}
          "Connections"]
         [{::ui/tab-id ::tab-logs
           :onClick    #(fm/set-value! this :ui/current-tab ::tab-logs)}
          "Logs"]])
      (case current-tab
        ::tab-logs
        (logs-view logs-view-data)
        (assistant/multi-parser-manager multi-parser)))))

(def connections-and-logs (fc/factory ConnectionsAndLogs))

(fc/defsc Root
  [this {:ui/keys [stuff]}]
  {:pre-merge  (fn [{:keys [current-normalized data-tree]}]
                 (merge {:ui/stuff {}} current-normalized data-tree))
   :query      [{:ui/stuff (fc/get-query ConnectionsAndLogs)}]
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
  (let [worker (pvw/use-worker {})]
    (h/$ (.-Provider pvw/WorkerContext) {:value worker}
      (h/$ (.-Provider com.wsscode.pathom.viz.fulcro/FulcroAppContext) {:value fc/*app*}
        (ui/column (ui/gc :.flex)
          (connections-and-logs stuff)
          (dom/div :.footer
            (dom/a {:href    "#"
                    :onClick (ui/prevent-default #(.openExternal shell "https://github.com/wilkerlucio/pathom-viz"))}
              "Pathom Viz")
            (dom/div (ui/gc :.flex))
            (dom/div "Freely distributed by "
              (dom/a {:href    "#"
                      :onClick (ui/prevent-default #(.openExternal shell "https://github.com/wilkerlucio"))}
                "Wilker Lucio"))))))))

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
