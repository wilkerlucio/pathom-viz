(ns com.wsscode.pathom.viz.query-plan
  (:require ["./d3-query-plan" :as d3qp]
            ["./detect-element-size" :refer [addResizeListener]]
            [com.fulcrologic.fulcro.components :as fc]
            [com.fulcrologic.fulcro.dom :as dom]))

(defn render-d3-graph-viz [graph]
  {::nodes {1 {}}})

(fc/defsc QueryPlanViz
  [this {::keys []}]
  {:css []}
  (dom/div
    ))

(def query-plan-viz (fc/factory QueryPlanViz))
