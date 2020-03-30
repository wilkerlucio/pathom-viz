(ns com.wsscode.pathom.viz.query-editor
  (:require [cljs.reader :refer [read-string]]
            [com.fulcrologic.fulcro-css.css :as css]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.fulcrologic.fulcro.components :as fc]
            [com.fulcrologic.fulcro.mutations :as fm]
            [com.wsscode.async.async-cljs :refer [<?maybe go-promise <!]]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.connect.planner :as pcp]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.viz.client-parser :as cp]
            [com.wsscode.pathom.viz.codemirror :as cm]
            [com.wsscode.pathom.viz.helpers :as pvh]
            [com.wsscode.pathom.viz.query-plan :as plan-view]
            [com.wsscode.pathom.viz.trace :as pvt]
            [com.wsscode.pathom.viz.lib.local-storage :as ls]))

(declare QueryEditor TransactionResponse)

(def remote-key :pathom-query-editor-remote)

(defn safe-read [s]
  (try
    (read-string s)
    (catch :default _ nil)))

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
                        :color      "#eaeaea"}]]]}
  (dom/button :.container props (fc/children this)))

(def button (fc/factory Button))

(defn load-query-editor-index [])

(fc/defsc QueryEditor
  [this
   {::keys                   [query result request-trace?]
    :ui/keys                 [query-running? plan-viewer]
    :com.wsscode.pathom/keys [trace]
    ::pc/keys                [indexes]}
   {::keys [editor-props enable-trace?
            default-trace-size
            default-plan-size
            default-query-size]
    :or    {enable-trace? true}}]
  {:initial-state     (fn [_]
                        {::id             (random-uuid)
                         ::request-trace? true
                         ::query          "[]"
                         ::result         ""
                         :ui/plan-viewer  {}})
   :pre-merge         (fn [{:keys [current-normalized data-tree]}]
                        (merge {::id             (random-uuid)
                                ::request-trace? true
                                ::query          "[]"
                                ::result         ""
                                :ui/plan-viewer  {}}
                          current-normalized data-tree))

   :ident             ::id
   :query             [::id
                       ::request-trace?
                       ::query
                       ::result
                       ::cp/parser-id
                       ::pc/indexes
                       :ui/query-running?
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
                       [:.plan {:display     "flex"}]]
   :css-include       [pvt/D3Trace Button]
   :componentDidMount (fn [this]
                        (js/setTimeout
                          #(fc/set-state! this {:render? true})
                          100))
   :initLocalState    (fn [this]
                        {:run-query (fn []
                                      (let [{:ui/keys  [query-running? plan-viewer]
                                             ::keys    [id query request-trace?]
                                             ::cp/keys [parser-id]
                                             :as       props} (fc/props this)
                                            {::keys [enable-trace?]
                                             :or    {enable-trace? true}} (fc/get-computed props)]
                                        (when-not query-running?
                                          (plan-view/set-plan-view-graph! this plan-viewer nil)
                                          (let [props' {::id                       id
                                                        ::request-trace?           (and request-trace? enable-trace?)
                                                        ::cp/parser-id             parser-id
                                                        ::cp/client-parser-request (safe-read query)}]
                                            (fc/transact! this [(run-query props')])))))})}
  (let [run-query          (fc/get-state this :run-query)
        css                (css/get-classnames QueryEditor)
        default-query-size (ls/get ::query-width (or default-query-size 400))
        default-trace-size (ls/get ::trace-height (or default-trace-size 400))
        default-plan-size  (ls/get ::plan-height (or default-plan-size 200))]
    (dom/div :.container
      (dom/div :.toolbar
        (if enable-trace?
          (dom/label
            (dom/input {:type     "checkbox"
                        :checked  request-trace?
                        :onChange #(fm/toggle! this ::request-trace?)})
            "Request trace"))
        (dom/div :.flex)
        (button {:onClick #(load-indexes (fc/any->app this) (fc/props this))
                 :style   {:marginRight "6px"}}
          "Refresh index")
        (button {:onClick  run-query
                 :disabled query-running?}
          "Run query"))

      (dom/div :.query-row
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
                                                   (js/console.log events))}))))

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
