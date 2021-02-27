(ns com.wsscode.pathom.viz.embed.main
  "Remote helpers to call remote parsers from the client."
  (:require
    ["react-dom" :as react-dom]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.wsscode.pathom.viz.embed.messaging :as p.viz.msg]
    [com.wsscode.pathom.viz.lib.hooks :as p.hooks]
    [com.wsscode.pathom.viz.styles]
    [com.wsscode.pathom.viz.trace-with-plan :as trace+plan]
    [com.wsscode.pathom3.viz.planner-explorer :as planner-explorer]
    [com.wsscode.pathom3.viz.plan :as viz-plan]
    [helix.core :as h]
    [helix.hooks :as hooks]))

(h/defnc EmbedTrace [{:keys [data]}]
  (trace+plan/trace-with-plan data))

(h/defnc LocalPlanStepper
  "Data should be:

    '{::pci/index-oir {}
      ::pcp/available-data {}
      ::eql/query []}"
  [{:keys [data]}]
  (h/$ viz-plan/PlanSnapshots
    {:frames
     (->> (viz-plan/compute-frames data)
          (mapv (juxt identity viz-plan/compute-plan-elements)))

     :display
     ::viz-plan/display-type-node-id}))

(h/defnc PlannerExplorer
  "Data can contain the initial index and query to use"
  [{:keys [data]}]
  (planner-explorer/planner-explorer data))

(def component-map
  {"trace"            EmbedTrace
   "plan-stepper"     LocalPlanStepper
   "planner-explorer" PlannerExplorer})

(defn render-child-component [{:keys [component-name component-props]}]
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

    (or (p.hooks/use-garden-css com.wsscode.pathom.viz.styles/full-css)
        (if @!component-contents
          (render-child-component @!component-contents)
          (dom/noscript)))))

(defn start []
  (react-dom/render
    (h/$ PathomVizEmbed {})
    (js/document.getElementById "app")))

(start)
