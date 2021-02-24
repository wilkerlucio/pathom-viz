(ns com.wsscode.pathom.viz.trace-with-plan
  (:require
    [com.fulcrologic.fulcro.components :as fc]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.wsscode.pathom.viz.helpers :as pvh]
    [com.wsscode.pathom.viz.timeline :as timeline]
    [com.wsscode.pathom.viz.trace :as pvt]
    [com.wsscode.pathom.viz.ui.kit :as ui]
    [com.wsscode.pathom3.connect.planner :as pcp]
    [com.wsscode.pathom3.viz.plan :as viz-plan]
    [goog.object :as gobj]
    [helix.core :as h]
    [helix.hooks :as hooks]))

(h/defnc TraceWithPlan [{:keys [trace]}]
  (let [stats             (some-> trace meta :com.wsscode.pathom3.connect.runner/run-stats)
        trace'            (pvh/use-memo #(if stats
                                           (timeline/compute-timeline-tree trace [])
                                           trace)
                            [(hash trace) (hash stats)])
        run-stats!        (pvh/use-fstate nil)
        selected-node-id! (pvh/use-fstate nil)
        plan-size         (pvh/use-persistent-state ::plan-size 200)
        display-type      (pvh/use-persistent-state ::viz-plan/display-type ::viz-plan/display-type-label)
        details-handle    (pvh/use-callback
                            (fn [events el]
                              (when-let [s (gobj/get el "run-stats")]
                                (run-stats! @s)
                                (selected-node-id!
                                  (some-> (gobj/get el "node") deref
                                          ::pcp/node-id)))
                              (js/console.log "Attribute trace:" events el))
                            [])
        select-node       (pvh/use-callback
                            (fn [node-id]
                              (selected-node-id! node-id))
                            [])]
    (pvh/use-effect
      (fn []
        (run-stats! nil))
      [(hash trace')])

    (dom/div :.flex.flex-1.flex-col.overflow-hidden.max-w-full.min-h-40
      (if trace
        (dom/div :.trace-wrapper.flex.flex-1.overflow-hidden.pt-3.min-h-40
          (pvt/d3-trace {:trace-data      trace'
                         :on-show-details details-handle})))

      (if @run-stats!
        (fc/fragment
          (ui/drag-resize
            {:state     plan-size
             :direction "down"}
            (dom/div {:className "flex items-center"}
              (dom/div :.flex-1 "Graph Viz")
              (ui/dom-select {:value    @display-type
                              :onChange #(reset! display-type %2)}
                (ui/dom-option {:value ::viz-plan/display-type-label} "Display: resolver name")
                (ui/dom-option {:value ::viz-plan/display-type-node-id} "Display: node id"))
              (dom/button {:onClick #(run-stats! nil)
                           :style   {:marginLeft 6}} "Close")))

          (dom/div :.flex.min-h-20 {:style {:height (str @plan-size "px")}}
            (h/$ viz-plan/PlanGraphWithNodeDetails
              {:run-stats      (assoc @run-stats! ::viz-plan/node-in-focus @selected-node-id!)
               :display-type   @display-type
               :on-select-node select-node})))))))

(defn trace-with-plan [trace] (h/$ TraceWithPlan {:trace trace}))
