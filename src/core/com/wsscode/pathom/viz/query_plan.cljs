(ns com.wsscode.pathom.viz.query-plan
  (:require ["./d3-query-plan" :as d3qp]
            ["./detect-element-size" :refer [addResizeListener]]
            [tangle.core :as tangle]
            [goog.object :as gobj]
            [com.fulcrologic.fulcro.components :as fc]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.connect.planner :as pcp]
            [com.wsscode.pathom.misc :as p.misc]
            [fulcro.client.primitives :as fp]))

(def node-size 30)
(def node-half-size (/ node-size 2))
(def node-space 60)
(def node-half-space (/ node-space 2))

(defn branches-count [{::pcp/keys [run-next] :as node}]
  (cond-> (count (pcp/node-branches node))
    run-next inc))

(defn layout-graph [graph]
  (let [graph' (pcp/compute-all-node-depths graph)
        depths (->> graph'
                    ::pcp/nodes
                    vals
                    (group-by ::pcp/node-depth))]
    (reduce-kv
      (fn [g k v]
        (reduce
          (fn [g [{::pcp/keys [node-id]} i]]
            (-> g
                (pcp/assoc-node node-id ::x (+ node-size (if (zero? (mod k 2)) (+ node-half-size node-half-space) 0) (* (+ node-size node-space) i)))
                (pcp/assoc-node node-id ::y (* (+ node-size node-space) k))
                (pcp/assoc-node node-id ::width node-size)
                (pcp/assoc-node node-id ::height node-size)))
          g
          (map vector (sort-by branches-count #(compare %2 %) v) (range))))
      graph'
      depths)))

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

(defn pos->coord [{::keys [x y]}]
  (str x "," y))

(defn create-path-line
  [pos-a pos-b]
  (str "M " (pos->coord pos-a) " L " (pos->coord pos-b)))

(defn create-path-curve
  [{xa ::x ya ::y :as pos-a} {xb ::x yb ::y :as pos-b}]
  (let [center     (+ ya (/ (- yb ya) 2))
        smoothness (/ (- xb xa) 8)]
    (str "M " (pos->coord pos-a) " C "
      (+ xa smoothness) "," center " "
      (- xb smoothness) "," center " "
      (pos->coord pos-b))))

(fc/defsc QueryPlanViz
  [this {::pcp/keys [graph]}]
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

     [:text {:font "bold 18px Verdana, Helvetica, Arial, sans-serif"}]

     [:.node {:fill "#ddd"}
      [:&.node-and {:fill "#cc0"}]
      [:&.node-or {:fill "#00c"}]]

     [:.line {:stroke       "#ef9d0e6b"
              :stroke-width "2px"
              :fill         "none"}
      [:&.line-focus {:stroke "#ef9d0eff"}]]

     [:.line-next {:stroke       "#0000006b"
                   :stroke-width "2px"
                   :fill         "none"}
      [:&.line-focus {:stroke "#000"}]]

     [:.line-focus {:stroke-width "3px"}]

     [:.label {:font-size   "11px"
               :text-align  "center"
               :margin "0"
               :padding-top "6px"}]]]}
  (dom/div :.container
    (let [graph' (layout-graph graph)
          focus  (fc/get-state this ::focus-node)]
      (dom/svg {:width "5000" :height "5000"}
        (for [{::keys     [x y width height]
               ::pc/keys  [sym]
               ::pcp/keys [node-id run-next]
               :as        node} (vals (::pcp/nodes graph'))]
          (let [start {::x (+ x (/ width 2)) ::y (+ y height)}
                cx    (+ x node-half-size)
                cy    (+ y node-half-size)]
            (fp/fragment {:key (str node-id)}
              (dom/circle :.node {:classes     [(cond
                                                  (::pcp/run-and node)
                                                  :.node-and
                                                  (::pcp/run-or node)
                                                  :.node-or)]
                                  :cx          cx
                                  :cy          cy
                                  :r           node-half-size
                                  :onClick     #(js/console.log node)
                                  :onMouseOver #(fc/set-state! this {::focus-node node-id})
                                  :onMouseOut  #(fc/set-state! this {::focus-node nil})})
              (dom/foreignObject {:x      (- x node-size)
                                  :y      (+ y node-size)
                                  :width  (* node-size 3)
                                  :height node-space}
                (dom/p :.label {:xmlns "http://www.w3.org/1999/xhtml"} (some-> sym name str)))
              #_(dom/text :.label {:x          cx
                                   :y          (+ cy node-size)
                                   :textLength 3}
                  (str sym))

              (for [next-node (mapv #(pcp/get-node graph' %) (pcp/node-branches node))]
                (dom/path :.line {:classes [(if (contains? #{node-id (::pcp/node-id next-node)} focus) :.line-focus)]
                                  :d       (create-path-curve start {::x (+ (::x next-node) (/ (::width next-node) 2)) ::y (::y next-node)})
                                  :key     (str node-id "->" (::pcp/node-id next-node))}))

              (if-let [next-node (pcp/get-node graph' run-next)]
                (dom/path :.line-next {:classes [(if (contains? #{node-id (::pcp/node-id next-node)} focus) :.line-focus)]
                                       :d       (create-path-curve start {::x (+ (::x next-node) (/ (::width next-node) 2)) ::y (::y next-node)})
                                       :key     (str node-id "->" (::pcp/node-id next-node))})))))))))

(def query-plan-viz (fc/factory QueryPlanViz))
