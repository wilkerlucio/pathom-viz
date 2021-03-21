(ns com.wsscode.pathom.viz.trace-with-plan
  (:require
    [com.fulcrologic.fulcro.components :as fc]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.wsscode.pathom.viz.client-parser :as cp]
    [com.wsscode.pathom.viz.helpers :as pvh]
    [com.wsscode.pathom.viz.timeline :as timeline]
    [com.wsscode.pathom.viz.trace :as pvt]
    [com.wsscode.pathom.viz.ui.kit :as ui]
    [com.wsscode.pathom3.connect.planner :as pcp]
    [com.wsscode.pathom3.viz.plan :as viz-plan]
    [goog.object :as gobj]
    [helix.core :as h]
    [com.wsscode.pathom.viz.app :as app]))

(defn log-plan-snapshots []
  (js/console.log "!! PARSERS" @cp/client-parsers))

(h/defnc TraceWithPlan [{:keys [trace]}]
  (let [stats             (some-> trace meta :com.wsscode.pathom3.connect.runner/run-stats)
        trace'            (pvh/use-memo #(if stats
                                           (timeline/compute-timeline-tree trace [])
                                           trace)
                            [(hash trace) (hash stats)])
        run-stats!        (pvh/use-fstate nil)
        selected-node-id! (pvh/use-fstate nil)
        {::cp/keys [parser-id]} (pvh/use-context app/ProviderContext)
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
    (js/console.log "!! CTX" parser-id)
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
            (dom/div {:className "flex items-center space-x-2"}
              (dom/div :.flex-1 "Graph Viz")
              (ui/button {:onClick log-plan-snapshots} "Log Plan Snapshots")
              (ui/dom-select {:value    @display-type
                              :onChange #(reset! display-type %2)}
                (ui/dom-option {:value ::viz-plan/display-type-label} "Display: resolver name")
                (ui/dom-option {:value ::viz-plan/display-type-node-id} "Display: node id"))
              (ui/button {:onClick #(run-stats! nil)} "Close")))

          (dom/div :.flex.min-h-20 {:style {:height (str @plan-size "px")}}
            (h/$ viz-plan/PlanGraphWithNodeDetails
              {:run-stats      (assoc @run-stats! ::viz-plan/node-in-focus @selected-node-id!)
               :display-type   @display-type
               :on-select-node select-node})))))))

(defn trace-with-plan [trace] (h/$ TraceWithPlan {:trace trace}))
