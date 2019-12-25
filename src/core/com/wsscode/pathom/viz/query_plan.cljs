(ns com.wsscode.pathom.viz.query-plan
  (:require ["./d3-query-plan" :as d3qp]
            ["./detect-element-size" :refer [addResizeListener]]
            [goog.object :as gobj]
            [com.fulcrologic.fulcro.components :as fc]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.connect.planner :as pcp]))

(defn render-d3-graph-viz [{::pcp/keys [nodes root]}]
  (let [links (into []
                    (mapcat
                      (fn [{::pcp/keys [run-next node-id] :as node}]
                        (let [branches (pcp/node-branches node)]
                          (cond-> (into []
                                        (map #(hash-map
                                                :source node-id
                                                :target %
                                                :branch? true))
                                        branches)
                            run-next
                            (conj {:source node-id :target run-next})))))
                    (vals nodes))]
    {:nodes (into [] (map #(-> %
                               (assoc :radius (+ 10
                                                (if (pcp/branch-node? %)
                                                  0
                                                  (or (some-> % ::pcp/requires count) 1))))
                               (cond-> (= root (::pcp/node-id %))
                                       (assoc :root? true)))) (vals nodes))
     :links links}))

(defn render-attribute-graph [this]
  (let [{::pcp/keys [graph]} (-> this fc/props)
        current   (gobj/get this "renderedData")
        container (gobj/get this "svgContainer")
        svg       (gobj/get this "svg")
        data      (clj->js (render-d3-graph-viz graph))]
    (if current ((gobj/get current "dispose")))
    (gobj/set svg "innerHTML" "")
    (js/console.log "RENDER DATA" (render-d3-graph-viz graph))
    (let [render-settings (d3qp/render svg
                            (clj->js {:svgWidth  (gobj/get container "clientWidth")
                                      :svgHeight (gobj/get container "clientHeight")
                                      :data      data}))]
      (gobj/set this "renderedData" render-settings))))

(fc/defsc QueryPlanViz
  [this {::keys []}]
  {:css
   [[:.container {:flex      1
                  :max-width "100%"}
     [:$pathom-viz-planner-attr-node
      {:fill "#000A"}

      [:&$pathom-viz-planner-attr-node-multi
       {:fill         "#00000021"
        :stroke       "#101010"
        :stroke-width "5px"}]

      [:&$pathom-viz-planner-node-branch-and
       {:fill "#f9e943e3"}]

      [:&$pathom-viz-planner-node-branch-or
       {:fill "#7ad1e8"}]

      [:&$pathom-viz-planner-node-root
       {:stroke       "#2596d6"
        :stroke-width "6px"}]]

     [:$pathom-viz-planner-arrow-provides
      [:path
       {:fill "#666"}]]
     [:$pathom-viz-planner-arrow-reaches
      [:path
       {:fill "#666"}]]

     [:$pathom-viz-planner-attr-link
      {:stroke         "#999"
       :stroke-opacity "0.6"
       :stroke-width   "1.5px"
       :fill           "none"}

      [:&$pathom-viz-planner-attr-link-focus-highlight
       {:stroke       "#4242e0db"
        :stroke-width "3px"
        :z-index      "10"}]

      [:&$pathom-viz-planner-attr-link-target-highlight
       {:stroke       "#0c0"
        :stroke-width "3px"
        :z-index      "10"}]

      [:&$pathom-viz-planner-attr-link-source-highlight
       {:stroke       "#cc1a9d"
        :stroke-width "2px"
        :z-index      "10"}]

      [:&$pathom-viz-planner-attr-link-branch
       {:stroke "orange"}]

      [:&$pathom-viz-planner-attr-link-reach
       {}]
      [:&$pathom-viz-planner-attr-link-deep
       {:stroke-dasharray "3px"}]]

     [:text {:font "bold 18px Verdana, Helvetica, Arial, sans-serif"}]]]

   :componentDidMount
   (fn [this]
     (render-attribute-graph this)
     #_(addResizeListener (gobj/get this "svgContainer") #(recompute-trace-size this)))

   :componentDidUpdate
   (fn [this prev-props _]
     (when (not= prev-props (-> this fc/props))
       (render-attribute-graph this)))

   :componentWillUnmount
   (fn [this]
     (if-let [settings (gobj/get this "renderedData")]
       ((gobj/get settings "dispose"))))

   :componentDidCatch
   (fn [this error info]
     (fc/set-state! this {::error-catch? true}))}
  (dom/div :.container {:ref #(gobj/set this "svgContainer" %)}
    (if (fc/get-state this ::error-catch?)
      (dom/div "Error rendering trace, check console for details")
      (dom/svg {:ref #(gobj/set this "svg" %)}))))

(def query-plan-viz (fc/factory QueryPlanViz))

(fc/defsc QueryPlanViz2
  [this {::pcp/keys [graph]}]
  (let [node     (pcp/get-root-node graph)
        children (->> (pcp/node-branches node)
                      (mapv #(pcp/get-node graph %))
                      (sort-by pcp/node->label))]
    (dom/div
      (for [node children]
        (dom/div {:key (::pcp/node-id node)} (pcp/node->label node))))))

(def query-plan-viz-2 (fc/factory QueryPlanViz2 {:keyfn ::id}))
