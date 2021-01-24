(ns com.wsscode.pathom.viz.trace-with-plan
  (:require [com.fulcrologic.fulcro.components :as fc]
            [com.wsscode.pathom.viz.query-plan :as plan-view]
            [com.fulcrologic.fulcro.mutations :as fm]
            [com.wsscode.pathom.viz.trace :as pvt]
            [com.wsscode.pathom.viz.helpers :as pvh]
            [com.wsscode.pathom3.connect.planner :as pcp]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.wsscode.pathom.viz.ui.kit :as ui]
            [com.wsscode.pathom.viz.timeline :as timeline]
            [com.wsscode.pathom3.viz.plan :as viz-plan]
            [goog.object :as gobj]
            [helix.core :as h]
            [clojure.core.protocols :as d]
            [com.wsscode.pathom3.interface.smart-map :as psm]
            [com.wsscode.pathom3.entity-tree :as p.ent]))

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
  (let [stats             (some-> trace meta :com.wsscode.pathom3.connect.runner/run-stats)
        trace'            (pvh/use-memo #(if stats
                                           (timeline/compute-timeline-tree trace [])
                                           trace)
                            [trace])
        !run-stats        (pvh/use-fstate nil)
        !selected-node-id (pvh/use-fstate nil)
        plan-size         (pvh/use-persistent-state ::plan-size 200)
        display-type      (pvh/use-persistent-state ::viz-plan/display-type ::viz-plan/display-type-label)
        details-handle    (pvh/use-callback
                            (fn [events el]
                              (when-let [s (gobj/get el "run-stats")]
                                (!run-stats @s)
                                (!selected-node-id
                                  (some-> (gobj/get el "node") deref
                                          ::pcp/node-id)))
                              (js/console.log "Attribute trace:" events el))
                            [])
        select-node       (pvh/use-callback
                            (fn [node-id]
                              (!selected-node-id node-id))
                            [])]
    (dom/div :.container
      (if trace
        (dom/div :.trace
          (pvt/d3-trace {::pvt/trace-data      trace'
                         ::pvt/on-show-details details-handle})))

      (if @!run-stats
        (fc/fragment
          (ui/drag-resize
            {:state     plan-size
             :direction "down"}
            (ui/row {:classes [:.center]}
              (dom/div (ui/gc :.flex) "Graph Viz")
              (ui/dom-select {:value    @display-type
                              :onChange #(reset! display-type %2)}
                (ui/dom-option {:value ::viz-plan/display-type-label} "Display: resolver name")
                (ui/dom-option {:value ::viz-plan/display-type-node-id} "Display: node id"))
              (ui/button {:onClick #(!run-stats nil)
                          :style   {:marginLeft 6}} "Close")))

          (dom/div :.plan {:style {:height (str @plan-size "px")}}
            (h/$ viz-plan/PlanGraphWithNodeDetails
              {:run-stats      (assoc @!run-stats ::viz-plan/node-in-focus @!selected-node-id)
               :display-type   @display-type
               :on-select-node select-node})))))))

(def trace-with-plan (fc/factory TraceWithPlan))
