(ns com.wsscode.pathom.viz.trace-with-plan
  (:require [com.fulcrologic.fulcro.components :as fc]
            [com.wsscode.pathom.viz.query-plan :as plan-view]
            [com.fulcrologic.fulcro.mutations :as fm]
            [com.wsscode.pathom.viz.trace :as pvt]
            [com.wsscode.pathom.viz.helpers :as pvh]
            [com.wsscode.pathom.connect.planner :as pcp]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.wsscode.pathom.viz.ui.kit :as ui]
            [com.wsscode.pathom.viz.timeline :as timeline]
            [com.wsscode.pathom3.viz.plan :as viz-plan]
            [helix.core :as h]))

(fc/defsc TraceWithPlan
  [this {:com.wsscode.pathom/keys [trace]}]
  {:css         [[:.container {:display        "flex"
                               :flex-direction "column"
                               :flex           "1"
                               :max-width      "100%"
                               :min-height     "200px"
                               :overflow       "hidden"}]
                 [:.trace {:display     "flex"
                           :flex        "1"
                           :padding-top "18px"
                           :overflow    "hidden"}]
                 [:.plan {:display "flex"}]]
   :css-include [pvt/D3Trace]
   :use-hooks?  true}
  (let [stats        (some-> trace meta :com.wsscode.pathom3.connect.runner/run-stats)
        trace'       (pvh/use-memo #(if stats
                                      (timeline/compute-timeline-tree trace [])
                                      trace)
                       [trace])
        plan-size    (pvh/use-persistent-state ::plan-size 200)
        display-type (pvh/use-persistent-state ::viz-plan/display-type ::viz-plan/display-type-label)]
    ;(js/console.log "!! TRACE" trace trace')
    (dom/div :.container
      (if trace
        (dom/div :.trace
          (pvt/d3-trace {::pvt/trace-data      trace'
                         ::pvt/on-show-details (fn [events node]
                                                 #_#_(plan-view/set-plan-view-graph! this plan-viewer
                                                       (plan-view/events->plan events))
                                                     (fc/transact! this [:ui/plan-viewer])
                                                 (js/console.log "Attribute trace:" events node))})))

      (if stats
        (fc/fragment
          (ui/drag-resize
            {:state     plan-size
             :direction "down"}
            (ui/row {:classes [:.center]}
              (dom/div (ui/gc :.flex) "Graph Viz")
              (ui/dom-select {:value    @display-type
                              :onChange #(reset! display-type %2)}
                (ui/dom-option {:value ::viz-plan/display-type-label} "Display: resolver name")
                (ui/dom-option {:value ::viz-plan/display-type-node-id} "Display: node id"))))

          (dom/div :.plan {:style {:height (str @plan-size "px")}}
            (h/$ viz-plan/PlanGraphView
              {:run-stats    stats
               :display-type @display-type})))))))

(def trace-with-plan (fc/factory TraceWithPlan))
