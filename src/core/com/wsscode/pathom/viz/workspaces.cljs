(ns com.wsscode.pathom.viz.workspaces
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
            [fulcro.client.primitives :as fp]
            [nubank.workspaces.card-types.fulcro :as ct.fulcro]
            [nubank.workspaces.lib.fulcro-portal :as f.portal]
            [nubank.workspaces.model :as wsm]))

(declare PathomCard PathomCardResponse)

;; Parser

(def indexes (atom {}))

(defmulti resolver-fn pc/resolver-dispatch)
(def defresolver (pc/resolver-factory resolver-fn indexes))

(defmulti mutation-fn pc/mutation-dispatch)
(def defmutation (pc/mutation-factory mutation-fn indexes))

(def card-parser
  (p/parallel-parser {::p/env          (fn [env]
                                         (merge
                                           {::p/reader             [p/map-reader pc/parallel-reader pc/open-ident-reader]
                                            ::pc/resolver-dispatch resolver-fn
                                            ::pc/mutate-dispatch   mutation-fn
                                            ::pc/indexes           @indexes}
                                           env))
                      ::pc/defresolver defresolver
                      ::pc/defmutation defmutation
                      ::p/mutate       pc/mutate-async
                      ::p/plugins      [p/error-handler-plugin
                                        p/request-cache-plugin
                                        (dissoc pc/connect-plugin ::pc/resolvers)
                                        p/trace-plugin]}))

(defresolver `indexes
  {::pc/output [::pc/indexes]}
  (fn [{::keys [client-parser]} _]
    (client-parser {} [{::pc/indexes [::pc/idents ::pc/index-io ::pc/autocomplete-ignore]}])))

(defmutation `run-query
  {::pc/params [::query]
   ::pc/output [::id ::result]}
  (fn [{::keys [client-parser]} {::keys [id query request-trace?]}]
    (go
      (let [pull-keys [:com.wsscode.pathom/trace]
            query     (cond-> (read-string query) request-trace? (conj :com.wsscode.pathom/trace))
            response  (<! (client-parser {} query))]
        (merge
          {::id                      id
           ::result                  (pvh/pprint (apply dissoc response pull-keys))
           :com.wsscode.pathom/trace nil}
          (select-keys response pull-keys))))))

(fm/defmutation run-query [_]
  (remote [{:keys [ast state]}]
    (fm/returning ast state PathomCardResponse)))

(defn load-indexes [app]
  (let [root-ident (-> app :reconciler fp/app-state deref :ui/root)]
    (df/load app root-ident PathomCard {:focus [::pc/indexes]})))

;; UI

(fp/defsc PathomCardResponse [_ _]
  {:ident [::id ::id]
   :query [::id ::result :com.wsscode.pathom/trace]})

(fp/defsc PathomCard
  [this {::keys                   [id query result request-trace?]
         :ui/keys                 [query-running?]
         :com.wsscode.pathom/keys [trace]
         ::pc/keys                [indexes]} _ css]
  {:initial-state (fn [_]
                    {::id             (random-uuid)
                     ::request-trace? true
                     ::query          ""
                     ::result         ""})
   :ident         [::id ::id]
   :query         [::id ::request-trace? ::query ::result :ui/query-running?
                   ::pc/indexes :com.wsscode.pathom/trace]
   :css           [[:$CodeMirror {:height   "100%"
                                  :width    "100%"
                                  :position "absolute"
                                  :z-index  "1"}
                    [:$cm-atom-composite {:color "#ab890d"}]
                    [:$cm-atom-ident {:color       "#219"
                                      :font-weight "bold"}]]
                   [:$CodeMirror-hint {:font-size "10px"}]
                   [:.container {:border         "1px solid #ddd"
                                 :display        "flex"
                                 :flex-direction "column"
                                 :flex           "1"
                                 :max-width      "100%"}]
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
                   [:.editor {}]
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
   :css-include   [pvt/D3Trace]}
  (let [run-query #(fp/ptransact! this [`(fm/set-props {:ui/query-running? true})
                                        `(run-query ~(fp/props this))
                                        `(fm/set-props {:ui/query-running? false})])]
    (dom/div :.container
      (dom/div :.toolbar
        (dom/label
          (dom/input {:type     "checkbox"
                      :checked  request-trace?
                      :onChange #(fm/toggle! this ::request-trace?)})
          "Request trace")
        (dom/div :.flex)
        (dom/button {:onClick  run-query
                     :disabled query-running?} "Run query"))
      (dom/div :.query-row
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
                    :onChange    #(fm/set-value! this ::query %)})
        (pvh/drag-resize this {:attribute :query-width
                               :axis      "x"
                               :default   400
                               :props     {:className (:divisor-v css)}}
          (dom/div))
        (cm/clojure {:className   (:result css)
                     :value       result
                     ::cm/options {::cm/readOnly    true
                                   ::cm/lineNumbers true}}))
      (if trace
        (pvh/drag-resize this {:attribute :trace-height
                               :default   400
                               :props     {:className (:divisor-h css)}}
          (dom/div)))
      (if trace
        (dom/div :.trace {:style {:height (str (or (fp/get-state this :trace-height) 400) "px")}}
          (pvt/d3-trace {::pvt/trace-data      trace
                         ::pvt/on-show-details #(js/console.log %)}))))))

(defn pathom-card-init
  [{::wsm/keys [card-id]
    :as        card}

   {::keys [parser]}]
  (let [{::wsm/keys       [refresh]
         ::ct.fulcro/keys [app*]
         :as              fulcro-card}
        (ct.fulcro/fulcro-card-init
          card
          {::f.portal/root PathomCard
           ::f.portal/app  {:started-callback
                            load-indexes

                            :networking
                            (pfn/pathom-remote
                              (fn [env tx]
                                (card-parser (assoc env ::client-parser parser) tx)))}})]
    (assoc fulcro-card
      ::wsm/refresh
      (fn [node]
        (load-indexes @app*)
        (refresh node)))))

(defn pathom-card [config]
  {::wsm/init #(pathom-card-init % config)

   ::wsm/align {:flex    1
                :display "flex"}})
