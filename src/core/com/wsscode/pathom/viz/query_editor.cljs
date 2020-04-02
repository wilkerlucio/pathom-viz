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
            [com.wsscode.pathom.viz.ui.kit :as ui]))

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
  (action [{:keys [state ref]}]
    (swap! state update-in ref assoc :ui/query-running? true))
  (ok-action [{:keys [state ref] :as env}]
    (let [response (pvh/env-parser-response env)]
      (swap! state update-in ref assoc
        :ui/query-running? false
        :com.wsscode.pathom/trace (get response :com.wsscode.pathom/trace)
        ::result (pvh/pprint (dissoc response :com.wsscode.pathom/trace)))))
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
  (let [{:ui/keys  [query-running? plan-viewer]
         ::keys    [id query request-trace?]
         ::cp/keys [parser-id]
         :as       props} (fc/props this)
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
                          :max-height    "32px"
                          :overflow      "auto"
                          :padding       "5px"
                          :white-space   "pre"}
          [:&:hover {:background "#9fdcff"}]]]}
  (dom/div :.container
    (dom/div :.title "History")
    (for [query (rseq query-history)]
      (dom/div :.history-item {:key     (hash query)
                               :onClick #(on-pick-query query %)}
        (str query)))))

(def history-view (fc/factory HistoryView))

(fc/defsc QueryEditor
  [this
   {::keys                   [query result request-trace? query-history]
    ::pc/keys                [indexes]
    :ui/keys                 [query-running? plan-viewer show-history?]
    :com.wsscode.pathom/keys [trace]}
   {::keys [editor-props enable-trace?
            default-trace-size
            default-plan-size
            default-query-size
            default-history-size]
    :or    {enable-trace? true}}]
  {:initial-state     (fn [_]
                        {::id              (random-uuid)
                         ::request-trace?  true
                         ::query           "[]"
                         ::result          ""
                         ::query-history   []
                         :ui/counter       0
                         :ui/show-history? true
                         :ui/plan-viewer   {}})
   :pre-merge         (fn [{:keys [current-normalized data-tree]}]
                        (merge {::id               (random-uuid)
                                ::request-trace?   true
                                ::query            "[]"
                                ::result           ""
                                ::query-history    []
                                :ui/counter        0
                                :ui/show-history?  true
                                :ui/query-running? false
                                :ui/plan-viewer    {}}
                          current-normalized data-tree))

   :ident             ::id
   :query             [::id
                       ::request-trace?
                       ::query
                       ::result
                       ::query-history
                       ::cp/parser-id
                       ::pc/indexes
                       :ui/counter
                       :ui/query-running?
                       :ui/show-history?
                       :com.wsscode.pathom/trace
                       {:ui/plan-viewer (fc/get-query plan-view/PlanViewWithDetails)}]
   :css               [[:$CodeMirror {:height   "100% !important"
                                      :width    "100% !important"
                                      :position "absolute !important"
                                      :z-index  "1"}
                        [:$cm-atom-composite {:color "#ab890d"}]
                        [:$cm-atom-ident {:color       "#219"
                                          :font-weight "bold"}]]
                       [:$CodeMirror-hint {:font-size "10px"}]
                       [:.container {:border         "1px solid #ddd"
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
                       [:.divisor-v {:width         "20px"
                                     :background    "#eee"
                                     :border        "1px solid #e0e0e0"
                                     :border-top    "0"
                                     :border-bottom "0"
                                     :z-index       "2"}]
                       [:.divisor-h {:height       "20px"
                                     :background   "#eee"
                                     :border       "1px solid #e0e0e0"
                                     :border-left  "0"
                                     :border-right "0"
                                     :z-index      "2"}]
                       [:.result {:flex     "1"
                                  :position "relative"}
                        [:$CodeMirror {:background "#f6f7f8"}]]
                       [:.trace {:display     "flex"
                                 :padding-top "18px"}]
                       [:.plan {:display "flex"}]
                       [:.history-container {:width      "250px"
                                             :max-height "100%"
                                             :overflow   "auto"}]]
   :css-include       [pvt/D3Trace Button HistoryView]
   #_#_:componentDidMount (fn [this]
                            (let [parser-id (-> this fc/props ::cp/parser-id)]
                              (df/load! this (fc/get-ident this) QueryEditor
                                {:focus  [::query-history ::id]
                                 :params {:pathom/context {::cp/parser-id parser-id}}}))
                            (js/setTimeout
                              #(fc/set-state! this {:render? true})
                              100))
   :initLocalState    (fn [this]
                        {:run-query (partial run-query! this)})
   :use-hooks?        true}
  (let [run-query            (fc/get-state this :run-query)
        css                  (css/get-classnames QueryEditor)
        show-history?        (ls/get ::show-history? true)
        default-history-size (ls/get ::history-width (or default-history-size 250))
        default-query-size   (ls/get ::query-width (or default-query-size 400))
        default-trace-size   (ls/get ::trace-height (or default-trace-size 400))
        default-plan-size    (ls/get ::plan-height (or default-plan-size 200))]
    (dom/div :.container
      (dom/div :.toolbar
        (if enable-trace?
          (dom/label
            (dom/input {:type     "checkbox"
                        :checked  request-trace?
                        :onChange #(fm/toggle! this ::request-trace?)})
            "Request trace"))
        (let [s (pvh/use-component-prop this :ui/counter)]
          (dom/button {:onClick #(swap! s inc)} (str "Counter " @s)))
        (dom/div :.flex)
        (button {:onClick  #(do
                              (fm/toggle! this :ui/show-history?)
                              (ls/set! ::show-history? (not show-history?)))
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
        (if (and show-history? (seq query-history))
          (fc/fragment
            (dom/div :.history-container {:style {:width (str (or (fc/get-state this :history-width) default-history-size) "px")}}
              (history-view {::query-history query-history
                             ::on-pick-query #(fm/set-value! this ::query %)}))
            (pvh/drag-resize this {:attribute      :history-width
                                   :persistent-key ::history-width
                                   :axis           "x"
                                   :key            "dragHandlerHistory"
                                   :default        default-history-size
                                   :props          {:className (:divisor-v css)}}
              (dom/div))))
        (when (fc/get-state this :render?)
          (cm/pathom
            (merge {:className   (:editor css)
                    :style       {:width (str (or (fc/get-state this :query-width) default-query-size) "px")}
                    :value       (or (str query) "")
                    ::pc/indexes (if (map? indexes) (p/elide-not-found indexes))
                    ::cm/options {::cm/extraKeys
                                  {"Cmd-Enter"   run-query
                                   "Ctrl-Enter"  run-query
                                   "Shift-Enter" run-query
                                   "Cmd-J"       "pathomJoin"
                                   "Ctrl-Space"  "autocomplete"}}
                    :onChange    #(fm/set-value! this ::query %)}
              editor-props)))
        (pvh/drag-resize this {:attribute      :query-width
                               :persistent-key ::query-width
                               :axis           "x"
                               :default        default-query-size
                               :props          {:className (:divisor-v css)}}
          (dom/div))
        (if (fc/get-state this :render?)
          (cm/clojure
            (merge {:className   (:result css)
                    :value       result
                    ::cm/options {::cm/readOnly    true
                                  ::cm/lineNumbers true}}
              editor-props))))

      (if trace
        (fc/fragment
          (pvh/drag-resize this {:attribute      :trace-height
                                 :persistent-key ::trace-height
                                 :default        default-trace-size
                                 :props          {:className (:divisor-h css)}}
            (dom/div))

          (dom/div :.trace {:style {:height (str (or (fc/get-state this :trace-height) default-trace-size) "px")}}
            (pvt/d3-trace {::pvt/trace-data      trace
                           ::pvt/on-show-details (fn [events]
                                                   (plan-view/set-plan-view-graph! this plan-viewer
                                                     (plan-view/events->plan events))
                                                   (fc/transact! this [:ui/plan-viewer])
                                                   (js/console.log "Attribute trace:" events))}))))

      (if (::pcp/graph plan-viewer)
        (fc/fragment
          (pvh/drag-resize this {:attribute      :plan-height
                                 :persistent-key ::plan-height
                                 :default        default-plan-size
                                 :props          {:className (:divisor-h css)}}
            (dom/div))

          (dom/div :.plan {:style {:height (str (or (fc/get-state this :plan-height) default-plan-size) "px")}}
            (plan-view/plan-view-with-details plan-viewer)))))))

(def query-editor (fc/computed-factory QueryEditor))
