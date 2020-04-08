(ns com.wsscode.pathom.viz.trace-with-plan
  (:require [com.fulcrologic.fulcro.components :as fc]
            [com.wsscode.pathom.viz.query-plan :as plan-view]
            [com.fulcrologic.fulcro.mutations :as fm]
            [com.wsscode.pathom.viz.trace :as pvt]
            [com.wsscode.pathom.viz.helpers :as pvh]
            [com.wsscode.pathom.connect.planner :as pcp]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.wsscode.pathom.viz.ui.kit :as ui]))

(fc/defsc TraceWithPlan
  [this
   {:ui/keys                 [plan-viewer]
    :com.wsscode.pathom/keys [trace]}]
  {:pre-merge   (fn [{:keys [current-normalized data-tree]}]
                  (let [id (or (::id data-tree)
                               (::id current-normalized)
                               (random-uuid))]
                    (merge {::id            id
                            :ui/plan-viewer {::plan-view/id id}}
                      current-normalized data-tree)))
   :ident       ::id
   :query       [::id
                 :com.wsscode.pathom/trace
                 {:ui/plan-viewer (fc/get-query plan-view/PlanViewWithDetails)}]
   :css         [[:.container {:display        "flex"
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
  (let [plan-size (pvh/use-persistent-state ::plan-size 200)]
    (dom/div :.container
      (if trace
        (dom/div :.trace
          (pvt/d3-trace {::pvt/trace-data      trace
                         ::pvt/on-show-details (fn [events]
                                                 (plan-view/set-plan-view-graph! this plan-viewer
                                                   (plan-view/events->plan events))
                                                 (fc/transact! this [:ui/plan-viewer])
                                                 (js/console.log "Attribute trace:" events))})))

      (if (::pcp/graph plan-viewer)
        (fc/fragment
          (pvh/drag-resize2
            {:state plan-size
             :props (ui/gc :.divisor-h)})

          (dom/div :.plan {:style {:height (str @plan-size "px")}}
            (plan-view/plan-view-with-details plan-viewer)))))))

(def trace-with-plan (fc/factory TraceWithPlan))
