(ns com.wsscode.pathom.viz.embed.main
  "Remote helpers to call remote parsers from the client."
  (:require
    ["react-dom" :as react-dom]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.wsscode.pathom.viz.embed.messaging :as p.viz.msg]
    [com.wsscode.pathom.viz.helpers :as pvh]
    [com.wsscode.pathom.viz.lib.hooks :as p.hooks]
    [com.wsscode.pathom.viz.trace-with-plan :as trace+plan]
    [com.wsscode.pathom3.viz.plan :as viz-plan]
    [com.wsscode.pathom3.viz.planner-explorer :as planner-explorer]
    [helix.core :as h]
    [helix.hooks :as hooks]))

(h/defnc EmbedTrace [{:keys [data]}]
  (trace+plan/trace-with-plan data))

(h/defnc PlanView [{:keys [data]}]
  (h/$ viz-plan/PlanGraphView
    {:run-stats      data
     :display-type   ::viz-plan/display-type-label
     :on-select-node identity}))

(h/defnc PlanViewWithDetails [{:keys [data]}]
  (let [selected-node-id! (pvh/use-fstate nil)]
    (h/$ viz-plan/PlanGraphWithNodeDetails
      {:run-stats      (assoc data ::viz-plan/node-in-focus @selected-node-id!)
       :display-type   ::viz-plan/display-type-label
       :on-select-node (pvh/use-callback selected-node-id! [])})))

(h/defnc LocalPlanStepper
  "Data should be:

    '{::pci/index-oir {}
      ::pcp/available-data {}
      ::eql/query []}

   This component should only be used for demoing the planner. The plan in this case will
   run with the Pathom version that's running in the embed, which may be different from your
   application version, potentially causing misleading results."
  [{:keys [data]}]
  (h/$ viz-plan/PlanSnapshots
    {:frames
     (->> (viz-plan/compute-frames data)
          (mapv (juxt identity viz-plan/compute-plan-elements)))

     :display
     ::viz-plan/display-type-label}))

(h/defnc PlanSnapshots
  [{:keys [data]}]
  (h/$ viz-plan/PlanSnapshots
    {:frames
     (viz-plan/prepare-frames data)

     :display
     ::viz-plan/display-type-label}))

(h/defnc PlannerExplorer
  "Data can contain the initial index and query to use"
  [{:keys [data]}]
  (planner-explorer/planner-explorer data))

(def component-map
  {:pathom.viz.embed.component/trace
   EmbedTrace

   :pathom.viz.embed.component/plan-view
   PlanView

   :pathom.viz.embed.component/plan-view-with-details
   PlanViewWithDetails

   :pathom.viz.embed.component/plan-stepper-demo
   LocalPlanStepper

   :pathom.viz.embed.component/plan-snapshots
   PlanSnapshots

   :pathom.viz.embed.component/planner-explorer
   PlannerExplorer})

(defn render-child-component [{:pathom.viz.embed/keys [component-name component-props]}]
  (if-let [Comp (get component-map component-name)]
    (h/$ Comp {:data component-props})
    (dom/div "Can't find component " component-name)))

(defonce *last-data (atom nil))

(h/defnc PathomVizEmbed []
  (let [!component-contents (p.hooks/use-fstate
                              (or (p.viz.msg/query-param-state)
                                  @*last-data))
        set-comp!           (hooks/use-callback []
                              (fn [x]
                                (reset! *last-data x)
                                (!component-contents x)))]
    (p.viz.msg/use-post-message-data set-comp!)

    (if @!component-contents
      (render-child-component @!component-contents)
      (dom/div {:classes ["loading-container"]}))))

(defn start []
  (react-dom/render
    (h/$ PathomVizEmbed {})
    (js/document.getElementById "app")))

(start)
