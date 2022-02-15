(ns com.wsscode.pathom.viz.query-editor
  (:require
    [cljs.spec.alpha :as s]
    [clojure.string :as str]
    [com.fulcrologic.fulcro-css.css :as css]
    [com.fulcrologic.fulcro-css.localized-dom :as dom]
    [com.fulcrologic.fulcro.components :as fc]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.mutations :as fm]
    [com.fulcrologic.guardrails.core :refer [>def >defn >fdef => | <- ?]]
    [com.wsscode.async.async-cljs :refer [<?maybe go-promise <!]]
    [com.wsscode.pathom.connect :as pc]
    [com.wsscode.pathom.viz.ws-connector.pathom3.adapter :refer [ensure-pathom2-indexes]]
    [com.wsscode.pathom.core :as p]
    [com.wsscode.pathom.misc :as p.misc]
    [com.wsscode.pathom.viz.client-parser :as cp]
    [com.wsscode.pathom.viz.codemirror :as cm]
    [com.wsscode.pathom.viz.codemirror6 :as cm6]
    [com.wsscode.pathom.viz.helpers :as pvh]
    [com.wsscode.pathom.viz.lib.local-storage :as ls]
    [com.wsscode.pathom.viz.trace :as pvt]
    [com.wsscode.pathom.viz.trace-with-plan :as trace+plan]
    [com.wsscode.pathom.viz.ui.kit :as ui]
    [com.wsscode.pathom3.viz.plan :as viz-plan]
    [helix.core :as h]
    [helix.hooks :as hooks]
    [com.wsscode.pathom3.connect.indexes :as pci]
    [com.wsscode.pathom3.format.shape-descriptor :as pfsd]
    [com.wsscode.pathom3.connect.operation :as pco]
    [com.wsscode.pathom.viz.timeline :as timeline]))

(declare QueryEditor TransactionResponse)

(>def ::query string?)
(>def ::query-history (s/coll-of ::query :kind vector?))

;; Helpers

(def history-max-size 100)

(defn history-remove [history query]
  (into [] (remove #{query}) history))

(defn history-append [history query]
  (let [excess (- (inc (count history)) history-max-size)]
    (-> (into [] (comp (remove #{query})
                       (drop (if (pos? excess) excess 0))) history)
        (p.misc/vconj query))))

(defn history-read [parser-id]
  (->> (ls/get [::query-history parser-id] [])
       (mapv #(if (string? %) {::query % ::entity nil} %))))

;; Registry

(pc/defmutation add-query-to-history-remote [_ {::keys [query entity] ::cp/keys [parser-id]}]
  {::pc/params [::query ::cp/parser-id]}
  (let [store-key       [::query-history parser-id]
        current-history (history-read parser-id)
        new-history     (history-append current-history {::query  query
                                                         ::entity entity})]
    (ls/set! store-key new-history)))

(pc/defmutation remove-query-from-history-remote [_ {::keys [query entity] ::cp/keys [parser-id]}]
  {::pc/params [::query ::cp/parser-id]}
  (let [store-key       [::query-history parser-id]
        current-history (history-read parser-id)
        new-history     (history-remove current-history {::query  query
                                                         ::entity entity})]
    (js/console.log "!! CUR" current-history {::query  query
                                              ::entity entity})
    (ls/set! store-key new-history)))

(pc/defresolver query-history-resolver
  [_env {::cp/keys [parser-id]}]
  {::pc/input  #{::cp/parser-id}
   ::pc/output [::query-history]}
  {::query-history (history-read parser-id)})

(def registry
  [add-query-to-history-remote
   remove-query-from-history-remote
   query-history-resolver])

;; Parser

(def index-query
  [{(pco/? ::pc/indexes)
    [(pco/? ::pc/index-attributes)
     (pco/? ::pc/idents)
     (pco/? ::pc/index-io)
     (pco/? ::pc/autocomplete-ignore)]}
   {(pco/? ::pci/indexes)
    [(pco/? ::pci/index-attributes)
     (pco/? ::pci/index-io)
     (pco/? ::pci/index-resolvers)
     (pco/? ::pci/transient-attrs)]}
   (pco/? :pathom.viz/support-boundary-interface?)])

#_:clj-kondo/ignore
(fm/defmutation run-query [{::keys [request-trace?]}]
  (action [{:keys [state ref] :as env}]
    (swap! state update-in ref assoc :ui/query-running? true))
  (ok-action [{:keys [state ref] :as env}]
    (let [response (pvh/env-parser-response env)]
      (swap! state update-in ref assoc
        :ui/query-running? false
        ::result (pvh/pprint-str (dissoc response :com.wsscode.pathom/trace)))
      (if-let [trace (timeline/response-trace response)]
        (pvh/swap-in! env [] assoc-in [:ui/trace-viewer :com.wsscode.pathom/trace] trace)
        (pvh/swap-in! env [] assoc :ui/trace-viewer nil))))
  (error-action [env]
    (js/console.log "QUERY ERROR" env))
  (remote [{:keys [ast state ref]}]
    (cond-> (assoc ast :key `cp/client-parser-request)
      request-trace?
      (update-in [:params ::cp/client-parser-request] conj :com.wsscode.pathom/trace))))

#_:clj-kondo/ignore
(fm/defmutation load-index [_]
  (action [{:keys [state ref]}]
    (swap! state update-in ref assoc :ui/query-running? true))
  (ok-action [{:keys [state ref] :as env}]
    (let [response       (pvh/env-parser-response env)
          idx            (-> response
                             p/elide-special-outputs
                             ensure-pathom2-indexes
                             ::pc/indexes)
          pathom-version (if (::pci/indexes response) 3 2)]
      (swap! state update-in ref assoc
        :ui/query-running? false
        :pathom.viz/support-boundary-interface? (or (= 3 pathom-version) (:pathom.viz/support-boundary-interface? response) false)
        :ui/pathom-version pathom-version
        :ui/completions (into [] (comp (filter keyword?)
                                       (remove (or (::pc/autocomplete-ignore idx) #{}))) (keys (::pc/index-attributes idx)))
        ::pc/indexes idx)))
  (error-action [env]
    (js/console.log "QUERY ERROR" env))
  (remote [{:keys [ast]}]
    (assoc ast :key `cp/client-parser-request)))

#_:clj-kondo/ignore
(fm/defmutation add-query-to-history [{::keys [query entity]}]
  (action [{:keys [state ref]}]
    (swap! state update-in (conj ref ::query-history) history-append {::query  query
                                                                      ::entity entity}))
  (remote [{:keys [ast]}]
    (assoc ast :key `add-query-to-history-remote)))

#_:clj-kondo/ignore
(fm/defmutation remove-query-from-history [{::keys [query entity]}]
  (action [{:keys [state ref]}]
    (swap! state update-in (conj ref ::query-history) history-remove {::query  query
                                                                      ::entity entity}))
  (remote [{:keys [ast]}]
    (assoc ast :key `remove-query-from-history-remote)))

(defn load-indexes
  [app {::keys    [id]
        ::cp/keys [parser-id]}]
  (let [props {::id                       id
               ::cp/parser-id             parser-id
               ::cp/client-parser-request index-query}]
    (fc/transact! app [(load-index props)]
      {:ref [::id id]})))

;; UI

(fc/defsc TransactionResponse [_ _]
  {:ident [::id ::id]
   :query [::id ::result :com.wsscode.pathom/trace]})

(defn load-query-editor-index [])

(defn trace-switch? [props]
  (let [v (get props :ui/pathom-version)]
    (and v (not= v 3))))

(defn run-query! [this]
  (let [{::keys [id]
         :as    props} (fc/props this)
        {:ui/keys         [query-running?]
         ::keys           [id query request-trace? entity]
         :pathom.viz/keys [support-boundary-interface?]
         ::cp/keys        [parser-id]} (get-in (fc/component->state-map this) [::id id])
        {::keys [enable-trace?]
         :or    {enable-trace? true}} (fc/get-computed props)
        entity-data (pvh/safe-read-string entity)]
    (when-not query-running?
      (if-let [query' (pvh/safe-read query)]
        (let [props' (cond->
                       {::id                       id
                        ::request-trace?           (and (trace-switch? props) request-trace? enable-trace?)
                        ::cp/parser-id             parser-id
                        ::cp/client-parser-request query'}

                       support-boundary-interface?
                       (assoc ::cp/client-parser-data (if (map? entity-data) entity-data)))]
          (fc/transact! this [(run-query props')
                              (add-query-to-history {::cp/parser-id parser-id
                                                     ::query        (str/trim query)
                                                     ::entity       (if (seq entity-data) entity)})]))))))

(fc/defsc HistoryView
  [_this
   {::keys [query-history
            on-pick-query
            on-delete-query]
    :or    {on-pick-query   identity
            on-delete-query identity}}]
  {:css [[:.container {}]
         [:.title {:background    "#eee"
                   :border-bottom "1px solid #ccc"
                   :padding       "6px"}
          ui/text-sans-13]
         [:.actions {:display "none"}]
         [:.history-item {:border-bottom "1px solid #ccc"
                          :cursor        "pointer"
                          :font-family   ui/font-code
                          :max-height    "45px"
                          :align-items   "center"
                          :overflow      "hidden"
                          :padding       "5px"
                          :white-space   "pre"
                          :display       "flex"}
          [:&:hover {:background ui/color-highlight}
           [:.actions {:display "flex"}]]]]}
  (dom/div :.container
    (dom/div :.title "History")
    (for [{::keys [query entity] :as entry} (rseq query-history)]
      (dom/div :.history-item {:key     (hash entry)
                               :onClick #(on-pick-query entry %)}
        (dom/div {:classes ["flex-1 overflow-auto"]}
          (dom/div (str query))
          (if entity
            (dom/div (str entity))))
        (dom/div :.actions
          {:onClick (fn [e]
                      (.stopPropagation e)
                      (on-delete-query entry e))}
          (dom/i {:classes ["fa" "fa-trash"]}))))))

(def history-view (fc/factory HistoryView))

(defn init-query-editor [this]
  (let [parser-id (-> this fc/props ::cp/parser-id)]
    (df/load! this (fc/get-ident this) QueryEditor
      {:focus  [::query-history ::id]
       :params {:pathom/context {::cp/parser-id parser-id}}}))
  (js/setTimeout
    #(fc/set-state! this {:render? true})
    100))

(h/defnc GraphWithOptions [{:keys [graph size]}]
  (let [[ds ds!] (hooks/use-state ::viz-plan/display-type-label)]
    (fc/fragment
      (ui/section-header {}
        (ui/row {}
          (dom/div (ui/gc :.flex) "Graph Viz")
          (ui/dom-select {:value    ds
                          :onChange #(ds! %2)}
            (ui/dom-option {:value ::viz-plan/display-type-label} "Display: resolver name")
            (ui/dom-option {:value ::viz-plan/display-type-node-id} "Display: node id"))))
      (dom/div :.trace {:style {:height (str size "px")}}
        (h/$ viz-plan/PlanGraphView
          {:run-stats    graph
           :display-type ds})))))

(fc/defsc QueryEditor
  [this
   {::keys           [query result request-trace? query-history]
    ::pc/keys        [indexes]
    ::cp/keys        [parser-id]
    :pathom.viz/keys [support-boundary-interface?]
    :ui/keys         [query-running? trace-viewer pathom-version completions]
    :as              props}
   {::keys [editor-props enable-trace?
            default-trace-size
            default-query-size
            default-history-size]
    :or    {enable-trace? true}}]
  {:pre-merge   (fn [{:keys [current-normalized data-tree]}]
                  (let [id (or (::id data-tree)
                               (::id current-normalized)
                               (random-uuid))]
                    (merge {::id                                    id
                            ::request-trace?                        true
                            ::query                                 "[]"
                            ::entity                                "{}"
                            ::result                                ""
                            ::query-history                         []
                            :pathom.viz/support-boundary-interface? nil
                            :ui/show-history?                       true
                            :ui/query-running?                      false}
                      current-normalized data-tree)))

   :ident       ::id
   :query       (fn [_]
                  [::id
                   ::request-trace?
                   ::query
                   ::entity
                   ::result
                   ::query-history
                   ::cp/parser-id
                   (pco/? ::pc/indexes)
                   :pathom.viz/support-boundary-interface?
                   :ui/query-running?
                   :ui/pathom-version
                   :ui/completions
                   :ui/show-history?
                   :com.wsscode.pathom/trace
                   :ui/graph-view
                   :ui/trace-viewer])
   :css         [[:.container {:border         "1px solid #ddd"
                               :display        "flex"
                               :flex-direction "column"
                               :flex           "1"
                               :max-width      "100%"
                               :min-height     "200px"}]
                 [:.title {:background    "#eee"
                           :border-bottom "1px solid #ccc"
                           :padding       "6px"}
                  ui/text-sans-13]
                 [:.query-row {:display  "flex"
                               :flex     "1"
                               :overflow "hidden"
                               :position "relative"}]
                 [:.toolbar {:background    "#eeeeee"
                             :border-bottom "1px solid #e0e0e0"
                             :padding       "5px 4px"
                             :display       "flex"
                             :align-items   "center"
                             :font-family   "sans-serif"
                             :font-size     "13px"}
                  [:label {:display     "flex"
                           :align-items "center"}
                   [:input {:margin-right "5px"}]]]
                 [:.flex {:flex "1"}]
                 [:.editor {:position "relative"}]
                 [:.result {:flex     "1"
                            :position "relative"}
                  [:$CodeMirror {:background "#f6f7f8"}]]
                 [:.trace {:display     "flex"
                           :padding-top "18px"
                           :overflow    "hidden"}]
                 [:.history-container {:width      "250px"
                                       :max-height "100%"
                                       :overflow   "auto"}]]
   :css-include [pvt/D3Trace HistoryView]
   :use-hooks?  true}
  (pvh/use-layout-effect #(init-query-editor this) [])
  (let [run-query          (pvh/use-callback #(run-query! this))
        css                (css/get-classnames QueryEditor)
        entity             (pvh/use-component-prop this ::entity)
        show-history?      (pvh/use-persistent-state ::show-history? true)
        history-size       (pvh/use-persistent-state ::history-width (or default-history-size 20))
        result-size        (pvh/use-persistent-state ::query-width (or default-query-size 400))
        entity-size        (pvh/use-persistent-state ::entity-height 10)
        trace-size         (pvh/use-persistent-state ::trace-height (or default-trace-size 30))
        query-data         (pvh/use-memo #(pvh/safe-read query) [query])
        entity-data        (pvh/use-memo #(some-> @entity pvh/safe-read) [@entity])
        entity-shape       (pvh/use-memo #(pfsd/data->shape-descriptor entity-data) [(hash entity-data)])

        p3?                (= 3 pathom-version)
        query-error        (cond
                             (nil? query-data)
                             "Invalid EDN"

                             (not (vector? query-data))
                             "EQL must start as a vector")

        entity-input-error (and p3?
                                (cond
                                  (nil? entity-data)
                                  "Invalid EDN"

                                  (not (map? entity-data))
                                  "Entity data must be a map."))]
    (dom/div :.container
      (dom/div :.toolbar
        (if (trace-switch? props)
          (if enable-trace?
            (dom/label
              (dom/input {:type     "checkbox"
                          :checked  request-trace?
                          :onChange #(fm/toggle! this ::request-trace?)})
              "Request trace")))
        (dom/div :.flex)
        (ui/button {:onClick  #(swap! show-history? not)
                    :style    {:marginRight "6px"}}
          "History")
        (ui/button {:onClick  #(load-indexes (fc/any->app this) (fc/props this))
                    :disabled query-running?
                    :style    {:marginRight "6px"}}
          "Refresh index")
        (ui/button {:onClick  run-query
                    :disabled query-running?}
          "Run query"))

      (dom/div :.query-row$min-h-20
        (if @show-history?
          (fc/fragment
            (dom/div :.history-container$min-w-40 {:style {:width (str @history-size "%")}}
              (history-view {::query-history   query-history
                             ::on-pick-query   #(do
                                                  (fm/set-value! this ::query (::query %))
                                                  (entity (or (::entity %) "{}")))
                             ::on-delete-query #(fc/transact! this [(remove-query-from-history (assoc % ::cp/parser-id parser-id))])}))
            (ui/drag-resize
              {:direction "left"
               :key       "dragHandlerHistory"
               :mode      "%"
               :state     history-size})))

        (dom/div {:classes ["flex-col flex-1"]}
          (dom/div :.title {:classes ["flex flex-row items-center" (if query-error "bg-yellow-300")]}
            (dom/div "EQL Request")
            (dom/div {:className "flex-1"})
            (if query-error
              (dom/div {:title query-error}
                (dom/span {:className "text-red-900 text-xs"} query-error))))
          (cm/pathom
            (merge {:className   (str (:editor css) " min-w-40")
                    :style       {:height (str @entity-size "%")}
                    :value       (or (str query) "")
                    ::pc/indexes (if (map? indexes) (p/elide-not-found (assoc indexes ::cm/available-data entity-shape)))
                    ::cm/options {::cm/extraKeys
                                  {"Cmd-Enter"   run-query
                                   "Ctrl-Enter"  run-query
                                   "Shift-Enter" run-query
                                   "Cmd-J"       "pathomJoin"
                                   "Ctrl-Space"  "autocomplete"}}
                    :onChange    #(fm/set-value! this ::query %)}
              editor-props))

          (ui/drag-resize
            {:direction "up"
             :mode      "%"
             :state     entity-size})
          (dom/div :.title {:classes ["flex flex-row items-center" (if entity-input-error "bg-yellow-300")]}
            (dom/div "Entity Data")
            (dom/div {:className "flex-1"})
            (if entity-input-error
              (dom/div {:title entity-input-error}
                (dom/span {:className "text-red-900 text-xs"} entity-input-error))))

          (if support-boundary-interface?
            (cm6/clojure-entity-write {:state            entity
                                       :props            {:classes ["flex-1 min-h-40"]}
                                       :completion-words completions})
            (dom/div {:classes ["flex-1 flex-row items-center justify-center text-gray-700 bg-gray-300"]}
              (if (false? support-boundary-interface?)
                (h/<>
                  "Entity data requires Pathom 2.4.0+" (dom/br)
                  "Or latest Pathom 3 from Git")))))

        (ui/drag-resize
          {:direction "right"
           :mode      "%"
           :state     result-size})

        (dom/div {:classes ["flex-col overflow-hidden"]
                  :style   {:width (str @result-size "%")}}
          (dom/div :.title "Result")
          (cm6/clojure-read result {:classes ["min-w-40 flex-1"]})))

      (if trace-viewer
        (fc/fragment
          (ui/drag-resize
            {:direction "down"
             :mode      "%"
             :state     trace-size})

          (dom/div :.trace$min-h-20 {:style {:height (str @trace-size "%")}}
            (trace+plan/trace-with-plan
              (:com.wsscode.pathom/trace trace-viewer)
              {:on-log-snaps
               (fn [snaps]
                 (fc/transact! this [(list 'com.wsscode.pathom.viz.electron.renderer.main/log-new-entry
                                       {:entry {:pathom.viz.log/type :pathom.viz.log.type/plan-snapshots
                                                :pathom.viz.log/data snaps}})]))})))))))

(def query-editor (fc/computed-factory QueryEditor))
