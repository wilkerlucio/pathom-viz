(ns com.wsscode.pathom.viz.query-plan-cards
  (:require [cljs.reader :refer [read-string]]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.fulcrologic.fulcro.components :as fc]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [com.fulcrologic.fulcro.mutations :as fm]
            [com.wsscode.common.async-cljs :refer [go-catch <!p]]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.connect.planner :as pcp]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.sugar :as ps]
            [com.wsscode.pathom.viz.helpers :as h]
            [com.wsscode.pathom.viz.query-plan :as plan-view]
            [goog.object :as gobj]
            [nubank.workspaces.card-types.fulcro3 :as ct.fulcro]
            [nubank.workspaces.core :as ws]
            [nubank.workspaces.model :as wsm]))

(pc/defresolver expand-thing-compound [env {:keys [thing-compound]}]
  {::pc/input  #{:thing-compound}
   ::pc/output [:thing/piece-a :thing/other-piece]}
  {:thing/piece-a (first thing-compound)
   :thing/other-piece (second thing-compound)})

(fc/defsc QueryPlanWrapper
  [this {::keys   [examples]
         :ui/keys [selected-example]}]
  {:pre-merge   (fn [{:keys [current-normalized data-tree]}]
                  (merge {::id                 (random-uuid)
                          :ui/selected-example ""} current-normalized data-tree))
   :ident       ::id
   :query       [::id
                 :ui/selected-example
                 ::examples]
   :css         [[:.container {:flex           1
                               :display        "flex"
                               :flex-direction "column"}]]
   :css-include [plan-view/QueryPlanViz]}
  (dom/div :.container
    (dom/div
      (dom/select {:value    selected-example
                   :onChange #(fm/set-string! this :ui/selected-example :event %)}
        (dom/option "Select example")
        (for [[title _] examples]
          (dom/option {:key title} title)))

      (dom/select {}
        (dom/option "Select renderer")))

    (if-let [graph (get examples selected-example)]
      (plan-view/query-plan-viz
        {::pcp/graph graph}))))

(pc/defresolver query-planner-examples [_ _]
  {::pc/output [{::examples [::title ::pcp/graph]}]}
  (go-catch
    (let [demos (-> (js/fetch "query-planner-demos.edn") <!p
                    (.text) <!p
                    read-string)]
      {::examples demos})))

(def parser
  (ps/connect-async-parser
    [query-planner-examples]))

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
