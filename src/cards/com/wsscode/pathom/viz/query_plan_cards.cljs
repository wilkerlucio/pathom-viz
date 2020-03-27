(ns com.wsscode.pathom.viz.query-plan-cards
  (:require [cljs.reader :refer [read-string]]
            [clojure.core.async :as async]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.fulcrologic.fulcro.components :as fc]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [com.fulcrologic.fulcro.mutations :as fm]
            [com.wsscode.async.async-cljs :refer [<?maybe go go-promise <!]]
            [com.wsscode.common.async-cljs :refer [go-catch <!p]]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.connect.foreign :as pcf]
            [com.wsscode.pathom.connect.planner :as pcp]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.sugar :as ps]
            [com.wsscode.pathom.trace :as pt]
            [com.wsscode.pathom.viz.codemirror :as cm]
            [com.wsscode.pathom.viz.helpers :as h]
            [com.wsscode.pathom.viz.query-plan :as plan-view]
            [com.wsscode.pathom.viz.trace :as trace]
            [goog.object :as gobj]
            [nubank.workspaces.card-types.fulcro3 :as ct.fulcro]
            [nubank.workspaces.core :as ws]
            [nubank.workspaces.model :as wsm]
            [com.wsscode.pathom.misc :as p.misc]))

(defn safe-read [s]
  (try
    (read-string {:readers {'error identity}}
      s)
    (catch :default _ nil)))

(pc/defresolver query-planner-examples [_ _]
  {::pc/output [{::examples [::title ::pcp/graph]}]}
  (go-catch
    (let [demos (-> (js/fetch "query-planner-demos.edn") <!p
                    (.text) <!p
                    read-string)]
      {::examples demos})))

(defonce indexes (atom {}))

(def registry
  [query-planner-examples
   (pc/constantly-resolver :answer 42)

   (pc/resolver 'slow
     {::pc/output [:slow]}
     (fn [_ _]
       (go-promise
         (<! (async/timeout 300))
         {:slow "slow"})))

   (pc/constantly-resolver :pi js/Math.PI)
   (pc/constantly-resolver :tau (* js/Math.PI 2))
   (pc/single-attr-resolver :pi :tau #(* % 2))
   (pc/alias-resolver :foreign :foreign->local)

   ; region errors

   (pc/resolver 'error
     {::pc/output [:error]}
     (fn [_ _]
       (throw (ex-info "Sync Error" {:error "data"}))))

   (pc/resolver 'maybe-error-error
     {::pc/output [:maybe-error]}
     (fn [_ _]
       (throw (ex-info "Sync Error" {:error "data"}))))

   (pc/resolver 'maybe-error-success
     {::pc/output [:maybe-error]}
     (fn [_ _]
       {:maybe-error "value"}))

   (pc/resolver 'error-with-dep
     {::pc/input  #{:pi}
      ::pc/output [:error-with-dep]}
     (fn [_ _]
       (throw (ex-info "Sync Error" {:error "data"}))))

   (pc/single-attr-resolver :error :error-dep pr-str)

   (pc/single-attr-resolver :error-dep :error-dep-dep pr-str)

   (pc/resolver 'error-async
     {::pc/output [:error-async]}
     (fn [_ _]
       (go-promise
         (throw (ex-info "Async Error" {:error "data"})))))

   (pc/resolver 'multi-dep-error
     {::pc/input  #{:error-with-dep :answer}
      ::pc/output [:multi-dep-error]}
     (fn [_ _]
       {:multi-dep-error "foi"}))

   (pc/resolver 'foreign-error-dep
     {::pc/input  #{:foreign-error}
      ::pc/output [:foreign-error-dep]}
     (fn [_ _]
       {:foreign-error-dep "foi"}))

   ; endregion
   ])

(def parser
  (p/async-parser
    {::p/env     {::p/reader               [{:foo (constantly "bar")}
                                            p/map-reader
                                            pc/reader3
                                            pc/open-ident-reader
                                            p/env-placeholder-reader]
                  ::p/placeholder-prefixes #{">"}}
     ::p/mutate  pc/mutate-async
     ::p/plugins [(pc/connect-plugin {::pc/indexes  indexes
                                      ::pc/register registry})
                  (pcf/foreign-parser-plugin {::pcf/parsers [(ps/connect-serial-parser
                                                               [(pc/constantly-resolver :foreign "value")
                                                                (pc/constantly-resolver :foreign2 "second value")
                                                                (pc/resolver 'foreign-error
                                                                  {::pc/output [:foreign-error]}
                                                                  (fn [_ _]
                                                                    (throw (ex-info "Foreign Error" {:error "data"}))))])]})
                  p/error-handler-plugin
                  p/trace-plugin]}))

(comment
  (go-promise
    (let [query [:foo]
          res   (<?maybe (parser {} (conj query :com.wsscode.pathom/trace)))]
      (js/console.log "TRACE" res))))

(defn dom-select [props & children]
  (apply dom/select
    (-> props
        (update :value pr-str)
        (update :onChange (fn [f]
                            (fn [e]
                              (f e (safe-read (.. e -target -value)))))))
    children))

(defn dom-option [props & children]
  (apply dom/option
    (-> props
        (update :value pr-str))
    children))

(fc/defsc QueryPlanWrapper
  [this {::keys   [examples]
         :ui/keys [plan query node-details label-kind trace-tree]}]
  {:pre-merge   (fn [{:keys [current-normalized data-tree]}]
                  (merge {::id           (random-uuid)
                          :ui/plan       nil
                          :ui/label-kind ::pc/sym
                          :ui/trace-tree nil
                          :ui/query      "[:foreign->local :maybe-error :multi-dep-error :tau :pi :answer :foreign-error-dep]"}
                    current-normalized
                    data-tree))
   :ident       ::id
   :query       [::id
                 ::examples
                 :ui/plan
                 :ui/label-kind
                 :ui/query
                 :ui/node-details
                 :ui/trace-tree]
   :css         [[:.container {:flex           1
                               :display        "flex"
                               :flex-direction "column"}]
                 [:.row {:display "flex"}]
                 [:.trace {:height "300px" :overflow "hidden"}]
                 [:.flex {:flex "1"}]
                 [:.editor {:height   "90px"
                            :overflow "hidden"}]
                 [:.node-details {:width "500px"}]]
   :css-include [plan-view/QueryPlanViz plan-view/NodeDetails trace/D3Trace]}
  (let [run-query (fn []
                    (go
                      (let [query      (-> this fc/props :ui/query safe-read)
                            t*         (atom [])
                            res        (<?maybe (parser {:com.wsscode.pathom.trace/trace* t*} query))
                            trace      (pt/compute-durations @t*)
                            plans      (->> trace
                                            (filter (comp #{::pc/compute-plan} ::pt/event))
                                            (filter (comp seq ::pcp/nodes ::pc/plan)))
                            trace-tree (pt/trace->viz @t*)]
                        (js/console.log "RES" res)
                        (fm/set-value! this :ui/plan nil)
                        (fm/set-value! this :ui/node-details nil)
                        (fm/set-value! this :ui/trace-tree trace-tree))))]
    (dom/div :.container
      #_(dom/div
          (dom/select {:value    plan
                       :onChange #(fm/set-string! this :ui/plan :event %)}
            (dom/option "Select example")
            (for [[title _] examples]
              (dom/option {:key title} title))))

      (dom/div :.editor
        (cm/pathom
          {:value       (or (str query) "")
           :onChange    #(fm/set-value! this :ui/query %)
           ::pc/indexes @indexes
           ::cm/options {::cm/extraKeys
                         {"Cmd-Enter"   run-query
                          "Ctrl-Enter"  run-query
                          "Shift-Enter" run-query}}}))

      (if trace-tree
        (dom/div :.trace
          (trace/d3-trace {::trace/trace-data      trace-tree
                           ::trace/on-show-details (fn [events]
                                                     (let [events' (mapv (comp safe-read #(gobj/get % "edn-original")) events)]
                                                       (if-let [plan (->> (filter (comp #{"reader3-execute"} :event) events')
                                                                          first :plan)]
                                                         (fm/set-value! this :ui/plan (update plan ::pcp/nodes
                                                                                        (fn [nodes]
                                                                                          (p.misc/map-vals pcp/integrate-node-log nodes))))
                                                         (fm/set-value! this :ui/node-details nil))
                                                       (js/console.log "details" events')))})))

      (dom/div {:style {:marginBottom "10px"}}
        (dom-select {:value    label-kind
                     :onChange #(fm/set-value! this :ui/label-kind %2)}
          (dom-option {:value ::pc/sym} "Node name")
          (dom-option {:value ::pcp/source-for-attrs} "Attribute source")))

      (dom/div :.row
        (dom/div :.flex
          (if-let [graph plan]
            (plan-view/query-plan-viz
              {::pcp/graph
               graph

               ::plan-view/selected-node-id
               (::pcp/node-id node-details)

               ::plan-view/label-kind
               label-kind

               ::plan-view/on-click-node
               (fn [e node]
                 (js/console.log "NODE" node)
                 (fm/set-value! this :ui/node-details
                   (if (= node-details node)
                     nil
                     node)))})))
        (if node-details
          (dom/div :.node-details
            (plan-view/node-details-ui node-details)))))))

(ws/defcard query-plan-card
  {::wsm/align ::wsm/stretch-flex}
  (ct.fulcro/fulcro-card
    {::ct.fulcro/root QueryPlanWrapper
     ::ct.fulcro/app  {:remotes
                       {:remote
                        (h/pathom-remote parser)}

                       :client-did-mount
                       (fn [app]
                         (js/console.log "MOUNTED")
                         (df/load! app [::id "singleton"] QueryPlanWrapper
                           {:target [:ui/root]}))}}))

(defn make-2d-grid [rows cells]
  (map vector
    (range)
    (cycle (mapcat #(repeat cells %) (range rows)))
    (cycle (range cells))))

(defn graph-planner-layout [graph]
  (-> graph
      pcp/compute-all-node-depths))

(comment
  (let [data  (clj->js (repeat 20 {}))
        rows  (js/Math.ceil (js/Math.sqrt (count data)))
        cells rows]
    (doseq [[i r c] (->> (make-2d-grid rows cells)
                         (take (count data)))]
      (gobj/extend (aget data i)
        #js {"x" c "y" r}))
    data))
