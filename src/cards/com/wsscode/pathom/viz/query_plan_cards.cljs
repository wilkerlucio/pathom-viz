(ns com.wsscode.pathom.viz.query-plan-cards
  (:require [cljs.reader :refer [read-string]]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.fulcrologic.fulcro.components :as fc]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [com.fulcrologic.fulcro.mutations :as fm]
            [com.wsscode.async.async-cljs :refer [<?maybe go go-promise <!]]
            [com.wsscode.common.async-cljs :refer [go-catch <!p]]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.connect.planner :as pcp]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.sugar :as ps]
            [com.wsscode.pathom.viz.codemirror :as cm]
            [com.wsscode.pathom.viz.helpers :as h]
            [com.wsscode.pathom.viz.query-plan :as plan-view]
            [goog.object :as gobj]
            [nubank.workspaces.card-types.fulcro3 :as ct.fulcro]
            [nubank.workspaces.core :as ws]
            [nubank.workspaces.model :as wsm]
            [com.wsscode.pathom.trace :as pt]
            [com.fulcrologic.fulcro.algorithms.merge :as merge]))

(defn safe-read [s]
  (try
    (read-string s)
    (catch :default _ nil)))

(pc/defresolver query-planner-examples [_ _]
  {::pc/output [{::examples [::title ::pcp/graph]}]}
  (go-catch
    (let [demos (-> (js/fetch "query-planner-demos.edn") <!p
                    (.text) <!p
                    read-string)]
      {::examples demos})))

(def parser
  (ps/connect-async-parser
    {::ps/connect-reader  pc/reader3
     ::ps/foreign-parsers [(ps/connect-serial-parser
                             [(pc/constantly-resolver :foreign "value")
                              (pc/constantly-resolver :foreign2 "second value")])]}
    [query-planner-examples
     (pc/constantly-resolver :answer 42)
     (pc/constantly-resolver :pi js/Math.PI)
     (pc/constantly-resolver :tao (/ js/Math.PI 2))
     (pc/single-attr-resolver :pi :tao #(/ % 2))
     (pc/alias-resolver :foreign :foreign->local)]))

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
         :ui/keys [selected-example query node-details label-kind]}]
  {:pre-merge   (fn [{:keys [current-normalized data-tree]}]
                  (merge {::id                 (random-uuid)
                          :ui/selected-example nil
                          :ui/label-kind       ::pc/sym
                          :ui/query            "[:com.wsscode.pathom.viz.query-plan-cards/examples]"}
                    current-normalized
                    data-tree))
   :ident       ::id
   :query       [::id
                 ::examples
                 :ui/selected-example
                 :ui/label-kind
                 :ui/query
                 :ui/node-details]
   :css         [[:.container {:flex           1
                               :display        "flex"
                               :flex-direction "column"}]
                 [:.editor {:height "90px"
                            :overflow "hidden"}]
                 [:.node-details {:width "500px"}]]
   :css-include [plan-view/QueryPlanViz plan-view/NodeDetails]}
  (let [run-query (fn []
                    (go
                      (let [query (-> this fc/props :ui/query safe-read)
                            t*    (atom [])
                            _     (<?maybe (parser {:com.wsscode.pathom.trace/trace* t*} (conj query :com.wsscode.pathom/trace)))
                            trace (pt/compute-durations @t*)
                            plans (filter (comp #{::pc/compute-plan} ::pt/event) trace)]
                        (fm/set-value! this :ui/selected-example (-> plans first ::pc/plan))
                        (fm/set-value! this :ui/node-details nil)
                        (js/console.log "PLANS" plans))))]
    (dom/div :.container
      #_(dom/div
          (dom/select {:value    selected-example
                       :onChange #(fm/set-string! this :ui/selected-example :event %)}
            (dom/option "Select example")
            (for [[title _] examples]
              (dom/option {:key title} title))))

      (dom/div :.editor
        (cm/pathom
          {:value       (or (str query) "")
           ::cm/options {::cm/extraKeys
                         {"Cmd-Enter"   run-query
                          "Ctrl-Enter"  run-query
                          "Shift-Enter" run-query}}
           :onChange    #(fm/set-value! this :ui/query %)}))

      (dom/div {:style {:marginBottom "10px"}}
        (dom-select {:value    label-kind
                     :onChange #(fm/set-value! this :ui/label-kind %2)}
          (dom-option {:value ::pc/sym} "Node name")
          (dom-option {:value ::pcp/source-for-attrs} "Attribute source")
          (dom-option {:value ::pcp/requires} "Requires")))

      (if-let [graph selected-example]
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
                 node)))}))

      (if node-details
        (dom/div :.node-details
          (plan-view/node-details node-details))))))

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
