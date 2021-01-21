(ns com.wsscode.pathom.viz.query-editor
  (:require [cljs.reader :refer [read-string]]
            [cljs.spec.alpha :as s]
            [com.fulcrologic.fulcro-css.css :as css]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.fulcrologic.fulcro.components :as fc]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [com.fulcrologic.fulcro.mutations :as fm]
            [com.fulcrologic.guardrails.core :refer [>def >defn >fdef => | <- ?]]
            [com.wsscode.async.async-cljs :refer [<?maybe go-promise <!]]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.connect.planner :as pcp]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.misc :as p.misc]
            [com.wsscode.pathom.viz.client-parser :as cp]
            [com.wsscode.pathom.viz.codemirror :as cm]
            [com.wsscode.pathom.viz.helpers :as pvh]
            [com.wsscode.pathom.viz.lib.local-storage :as ls]
            [com.wsscode.pathom.viz.query-plan :as plan-view]
            [com.wsscode.pathom.viz.trace :as pvt]
            [com.wsscode.pathom.viz.trace-with-plan :as trace+plan]
            [com.wsscode.pathom.viz.ui.kit :as ui]
            [helix.core :as h]
            [com.wsscode.pathom3.viz.plan :as viz-plan]
            [helix.hooks :as hooks]))

(declare QueryEditor TransactionResponse)

(>def ::query string?)
(>def ::query-history (s/coll-of ::query :kind vector?))

;; Helpers

(def history-max-size 100)

(defn history-append [history query]
  (-> (into [] (comp (remove #{query})
                     (take (dec history-max-size))) history)
      (p.misc/vconj query)))

;; Registry

(pc/defmutation add-query-to-history-remote [_ {::keys [query] ::cp/keys [parser-id]}]
  {::pc/params [::query ::cp/parser-id]}
  (let [store-key       [::query-history parser-id]
        current-history (ls/get store-key [])
        new-history     (history-append current-history query)]
    (ls/set! store-key new-history)))

(pc/defresolver query-history-resolver [env {::cp/keys [parser-id]}]
  {::pc/input  #{::cp/parser-id}
   ::pc/output [::query-history]}
  {::query-history (ls/get [::query-history parser-id] [])})

(def registry [add-query-to-history-remote query-history-resolver])

;; Parser

(def index-query
  [{::pc/indexes [::pc/idents ::pc/index-io ::pc/autocomplete-ignore]}])

(pc/defresolver indexes [{::keys [client-parser]} _]
  {::pc/output [::pc/indexes]}
  (client-parser {} index-query))

(fm/defmutation run-query [{::keys [request-trace?]}]
  (action [{:keys [state ref] :as env}]
    (swap! state update-in ref assoc :ui/query-running? true))
  (ok-action [{:keys [state ref] :as env}]
    (let [response (pvh/env-parser-response env)]
      (swap! state update-in ref assoc
        :ui/query-running? false
        ::result (pvh/pprint (dissoc response :com.wsscode.pathom/trace)))
      (pvh/swap-in! env [] assoc :ui/graph-view (-> response meta :com.wsscode.pathom3.connect.runner/run-stats))
      (pvh/swap-in! env [:ui/trace-viewer] assoc
        :com.wsscode.pathom/trace (get response :com.wsscode.pathom/trace))
      (pvh/swap-in! env [:ui/trace-viewer :ui/plan-viewer] assoc
        ::pcp/graph nil
        :ui/node-details nil)))
  (error-action [env]
    (js/console.log "QUERY ERROR" env))
  (remote [{:keys [ast]}]
    (cond-> (assoc ast :key `cp/client-parser-mutation)
      request-trace?
      (update-in [:params ::cp/client-parser-request] conj :com.wsscode.pathom/trace))))

(fm/defmutation load-index [_]
  (action [{:keys [state ref]}]
    (swap! state update-in ref assoc :ui/query-running? true))
  (ok-action [{:keys [state ref] :as env}]
    (let [response (pvh/env-parser-response env)]
      (swap! state update-in ref assoc
        :ui/query-running? false
        ::pc/indexes (-> response
                         p/elide-special-outputs
                         ::pc/indexes))))
  (error-action [env]
    (js/console.log "QUERY ERROR" env))
  (remote [{:keys [ast]}]
    (assoc ast :key `cp/client-parser-mutation)))

(fm/defmutation add-query-to-history [{::keys [query]}]
  (action [{:keys [state ref]}]
    (swap! state update-in (conj ref ::query-history) history-append query))
  (remote [{:keys [ast]}]
    (assoc ast :key `add-query-to-history-remote)))

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

(fc/defsc Button
  [this props]
  {:css [[:.container
          {:font-size   "11px"
           :font-family "'Open Sans', sans-serif"
           :font-weight "600"}
          {:background-color "#4b5b6d"
           :border           "none"
           :border-radius    "3px"
           :color            "#fff"
           :cursor           "pointer"
           :display          "inline-block"
           :padding          "2px 8px"
           :line-height      "1.5"
           :margin-bottom    "0"
           :text-align       "center"
           :white-space      "nowrap"
           :vertical-align   "middle"
           :user-select      "none"
           :outline          "none"}
          [:&:disabled {:background "#b0c1d6"
                        :color      "#eaeaea"
                        :cursor     "not-allowed"}]]]}
  (dom/button :.container props (fc/children this)))

(def button (fc/factory Button))

(defn load-query-editor-index [])

(defn run-query! [this]
  (let [{::keys [id]
         :as    props} (fc/props this)
        {:ui/keys  [query-running? plan-viewer]
         ::keys    [id query request-trace?]
         ::cp/keys [parser-id]} (get-in (fc/component->state-map this) [::id id])
        {::keys [enable-trace?]
         :or    {enable-trace? true}} (fc/get-computed props)]
    (when-not query-running?
      (plan-view/set-plan-view-graph! this plan-viewer nil)
      (if-let [query' (pvh/safe-read query)]
        (let [props' {::id                       id
                      ::request-trace?           (and request-trace? enable-trace?)
                      ::cp/parser-id             parser-id
                      ::cp/client-parser-request query'}]
          (fc/transact! this [(run-query props')
                              (add-query-to-history {::cp/parser-id parser-id
                                                     ::query        query})]))))))

(fc/defsc HistoryView
  [this {::keys [query-history
                 on-pick-query]
         :or    {on-pick-query identity}}]
  {:css [[:.container {}]
         [:.title {:background "#eee"
                   :border-bottom "1px solid #ccc"
                   :padding "6px"}
          ui/text-sans-13]
         [:.history-item {:border-bottom "1px solid #ccc"
                          :cursor        "pointer"
                          :font-family   ui/font-code
                          :max-height    "45px"
                          :overflow      "auto"
                          :padding       "5px"
                          :white-space   "pre"}
          [:&:hover {:background ui/color-highlight}]]]}
  (dom/div :.container
    (dom/div :.title "History")
    (for [query (rseq query-history)]
      (dom/div :.history-item {:key     (hash query)
                               :onClick #(on-pick-query query %)}
        (str query)))))

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
   {::keys    [query result request-trace? query-history]
    ::pc/keys [indexes]
    :ui/keys  [query-running? trace-viewer graph-view]}
   {::keys [editor-props enable-trace?
            default-trace-size
            default-query-size
            default-history-size]
    :or    {enable-trace? true}}]
  {:pre-merge   (fn [{:keys [current-normalized data-tree]}]
                  (let [id (or (::id data-tree)
                               (::id current-normalized)
                               (random-uuid))]
                    (merge {::id               id
                            ::request-trace?   true
                            ::query            "[]"
                            ::result           ""
                            ::query-history    []
                            :ui/show-history?  true
                            :ui/query-running? false
                            :ui/trace-viewer   {::trace+plan/id id}}
                      current-normalized data-tree)))

   :ident       ::id
   :query       [::id
                 ::request-trace?
                 ::query
                 ::result
                 ::query-history
                 ::cp/parser-id
                 ::pc/indexes
                 :ui/query-running?
                 :ui/show-history?
                 :com.wsscode.pathom/trace
                 :ui/graph-view
                 {:ui/trace-viewer (fc/get-query trace+plan/TraceWithPlan)}]
   :css         [[:.container {:border         "1px solid #ddd"
                               :display        "flex"
                               :flex-direction "column"
                               :flex           "1"
                               :max-width      "100%"
                               :min-height     "200px"}]
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
   :css-include [pvt/D3Trace Button HistoryView]
   :use-hooks?  true}
  (pvh/use-layout-effect #(init-query-editor this) [])
  (let [run-query     (pvh/use-callback #(run-query! this))
        css           (css/get-classnames QueryEditor)
        show-history? (pvh/use-persistent-state ::show-history? true)
        history-size  (pvh/use-persistent-state ::history-width (or default-history-size 250))
        query-size    (pvh/use-persistent-state ::query-width (or default-query-size 400))
        trace-size    (pvh/use-persistent-state ::trace-height (or default-trace-size 200))]
    (dom/div :.container
      (dom/div :.toolbar
        (if enable-trace?
          (dom/label
            (dom/input {:type     "checkbox"
                        :checked  request-trace?
                        :onChange #(fm/toggle! this ::request-trace?)})
            "Request trace"))
        (dom/div :.flex)
        (button {:onClick  #(swap! show-history? not)
                 :disabled (not (seq query-history))
                 :style    {:marginRight "6px"}}
          "History")
        (button {:onClick #(load-indexes (fc/any->app this) (fc/props this))
                 :style   {:marginRight "6px"}}
          "Refresh index")
        (button {:onClick  run-query
                 :disabled query-running?}
          "Run query"))

      (dom/div :.query-row
        (if (and @show-history? (seq query-history))
          (fc/fragment
            (dom/div :.history-container {:style {:width (str @history-size "px")}}
              (history-view {::query-history query-history
                             ::on-pick-query #(fm/set-value! this ::query %)}))
            (ui/drag-resize
              {:direction "left"
               :key       "dragHandlerHistory"
               :state     history-size})))

        (cm/pathom
          (merge {:className   (:editor css)
                  :style       {:width (str @query-size "px")}
                  :value       (or (str query) "")
                  ::pc/indexes (if (map? indexes) (p/elide-not-found indexes))
                  ::cm/options {::cm/extraKeys
                                {"Cmd-Enter"   run-query
                                 "Ctrl-Enter"  run-query
                                 "Shift-Enter" run-query
                                 "Cmd-J"       "pathomJoin"
                                 "Ctrl-Space"  "autocomplete"}}
                  :onChange    #(fm/set-value! this ::query %)}
            editor-props))

        (ui/drag-resize
          {:direction "left"
           :state     query-size})

        (cm/clojure
          (merge {:className   (:result css)
                  :value       result
                  ::cm/options {::cm/readOnly    true
                                ::cm/lineNumbers true}}
            editor-props)))

      (let [ds (pvh/use-persistent-state ::viz-plan/display-type ::viz-plan/display-type-label)]
        (if graph-view
          (fc/fragment
            (ui/drag-resize
              {:direction "down"
               :state     trace-size})

            (fc/fragment
              (ui/section-header {}
                (ui/row {}
                  (dom/div (ui/gc :.flex) "Graph Viz")
                  (ui/dom-select {:value    @ds
                                  :onChange #(reset! ds %2)}
                    (ui/dom-option {:value ::viz-plan/display-type-label} "Display: resolver name")
                    (ui/dom-option {:value ::viz-plan/display-type-node-id} "Display: node id"))))
              (dom/div {:style {:display    "flex"
                                :paddingTop "18px"
                                :overflow   "hidden"
                                :height     (str @trace-size "px")}}
                (h/$ viz-plan/PlanGraphView
                  {:run-stats    graph-view
                   :display-type @ds}))))))

      (if (:com.wsscode.pathom/trace trace-viewer)
        (fc/fragment
          (ui/drag-resize
            {:direction "down"
             :state     trace-size})

          (dom/div :.trace {:style {:height (str @trace-size "px")}}
            (trace+plan/trace-with-plan trace-viewer)))))))

(def query-editor (fc/computed-factory QueryEditor))
