(ns com.wsscode.pathom.viz.query-editor
  (:require [cljs.core.async :refer [go <!]]
            [cljs.reader :refer [read-string]]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.fulcro.network :as pfn]
            [com.wsscode.pathom.viz.codemirror :as cm]
            [com.wsscode.pathom.viz.helpers :as pvh]
            [com.wsscode.pathom.viz.trace :as pvt]
            [fulcro.client.data-fetch :as df]
            [fulcro.client.localized-dom :as dom]
            [fulcro.client.mutations :as fm]
            [fulcro.client.primitives :as fp]))

(declare QueryEditor TransactionResponse)

(def remote-key :pathom-query-editor-remote)

;; Parser

(pc/defresolver indexes [{::keys [client-parser]} _]
  {::pc/output [::pc/indexes]}
  (client-parser {} [{::pc/indexes [::pc/idents ::pc/index-io ::pc/autocomplete-ignore]}]))

(pc/defmutation run-query [{::keys [client-parser]} {::keys [id query request-trace?]}]
  {::pc/params [::query]
   ::pc/output [::id ::result]}
  (go
    (let [pull-keys [:com.wsscode.pathom/trace]
          query     (cond-> (read-string query) request-trace? (conj :com.wsscode.pathom/trace))
          response  (<! (client-parser {} query))]
      (merge
        {::id                      id
         ::result                  (pvh/pprint (apply dissoc response pull-keys))
         :com.wsscode.pathom/trace nil}
        (select-keys response pull-keys)))))

(def card-parser
  (p/parallel-parser {::p/env     {::p/reader [p/map-reader pc/parallel-reader pc/open-ident-reader]}
                      ::p/mutate  pc/mutate-async
                      ::p/plugins [p/error-handler-plugin
                                   p/request-cache-plugin
                                   (-> (pc/connect-plugin {::pc/register [indexes run-query]})
                                       (dissoc ::pc/register))
                                   p/trace-plugin]}))

(defn client-card-parser
  "Returns a new parser that will use the card-parser setting the client
  parser to be `client-parser`."
  [client-parser]
  (fn [env tx]
    (card-parser (assoc env ::client-parser client-parser) tx)))

(fm/defmutation run-query [_]
  (pathom-query-editor-remote [{:keys [ast state]}]
    (fm/returning ast state TransactionResponse)))

(defn load-indexes
  [app-or-reconciler]
  (let [reconciler (or (:reconciler app-or-reconciler) app-or-reconciler)
        root-ident (-> reconciler fp/app-state deref :ui/root)]
    (df/load reconciler root-ident QueryEditor
      {:focus  [::pc/indexes]
       :remote remote-key})))

;; UI

(fp/defsc TransactionResponse [_ _]
  {:ident [::id ::id]
   :query [::id ::result :com.wsscode.pathom/trace]})

(fp/defsc Button
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
  (dom/button :.container props (fp/children this)))

(def button (fp/factory Button))

(fp/defsc QueryEditor
  [this
   {::keys                        [query result request-trace?]
         :ui/keys                 [query-running?]
         :com.wsscode.pathom/keys [trace]
    ::pc/keys                     [indexes]}
   {::keys [default-trace-size]
    :or {default-trace-size 400}} css]
  {:initial-state     (fn [_]
                        {::id             (random-uuid)
                         ::request-trace? true
                         ::query          "[]"
                         ::result         ""})
   :ident             [::id ::id]
   :query             [::id ::request-trace? ::query ::result :ui/query-running?
                       ::pc/indexes :com.wsscode.pathom/trace]
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
                                 :padding-top "18px"}]]
   :css-include       [pvt/D3Trace Button]
   :componentDidMount (fn []
                        (js/setTimeout
                          #(fp/set-state! this {:render? true})
                          100))
   :initLocalState    (fn []
                        {:run-query (fn []
                                      (let [{:ui/keys [query-running?] :as props} (fp/props this)]
                                        (if-not query-running?
                                          (fp/ptransact! this [`(fm/set-props {:ui/query-running? true})
                                                               `(run-query ~props)
                                                               `(fm/set-props {:ui/query-running? false})]))))})}
  (let [run-query (fp/get-state this :run-query)]
    (dom/div :.container

      (dom/div :.toolbar
        (dom/label
          (dom/input {:type     "checkbox"
                      :checked  request-trace?
                      :onChange #(fm/toggle! this ::request-trace?)})
          "Request trace")
        (dom/div :.flex)
        (button {:onClick #(load-indexes (fp/get-reconciler this))
                 :style   {:marginRight "6px"}}
          "Refresh index")
        (button {:onClick  run-query
                 :disabled query-running?}
          "Run query"))

      (dom/div :.query-row
        (if (fp/get-state this :render?)
          (cm/pathom {:className   (:editor css)
                      :style       {:width (str (or (fp/get-state this :query-width) 400) "px")}
                      :value       (or (str query) "")
                      ::pc/indexes (p/elide-not-found indexes)
                      ::cm/options {::cm/extraKeys
                                    {"Cmd-Enter"   run-query
                                     "Ctrl-Enter"  run-query
                                     "Shift-Enter" run-query
                                     "Cmd-J"       "pathomJoin"
                                     "Ctrl-Space"  "autocomplete"}}
                      :onChange    #(fm/set-value! this ::query %)}))
        (pvh/drag-resize this {:attribute :query-width
                               :axis      "x"
                               :default   400
                               :props     {:className (:divisor-v css)}}
          (dom/div))
        (if (fp/get-state this :render?)
          (cm/clojure {:className   (:result css)
                       :value       result
                       ::cm/options {::cm/readOnly    true
                                     ::cm/lineNumbers true}})))
      (if trace
        (pvh/drag-resize this {:attribute :trace-height
                               :default   default-trace-size
                               :props     {:className (:divisor-h css)}}
          (dom/div)))
      (if trace
        (dom/div :.trace {:style {:height (str (or (fp/get-state this :trace-height) default-trace-size) "px")}}
          (pvt/d3-trace {::pvt/trace-data      trace
                         ::pvt/on-show-details #(js/console.log %)}))))))

(def query-editor (fp/computed-factory QueryEditor))
