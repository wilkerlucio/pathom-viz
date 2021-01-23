(ns com.wsscode.pathom.viz.query-plan
  (:require ["./d3-query-plan" :as d3qp]
            ["./detect-element-size" :refer [addResizeListener]]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.fulcrologic.fulcro.components :as fc]
            [com.fulcrologic.fulcro.mutations :as fm]
            [com.fulcrologic.guardrails.core :refer [>def >defn >fdef => | <- ?]]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.connect.planner :as pcp]
            [com.wsscode.pathom.misc :as p.misc]
            [com.wsscode.pathom.viz.helpers :as pvh]
            [com.wsscode.pathom.viz.lib.local-storage :as ls]
            [com.wsscode.pathom.viz.ui.kit :as ui]
            [edn-query-language.core :as eql]
            [goog.object :as gobj]))

(>def ::on-click-node fn?)
(>def ::on-mouse-over-node fn?)
(>def ::on-mouse-out-node fn?)
(>def ::selected-node-id ::pcp/node-id)

(def node-size 30)
(def node-half-size (/ node-size 2))
(def node-space 60)
(def node-half-space (/ node-space 2))

(defn detail-info [title content]
  (dom/div
    (dom/div :.label title)
    (dom/div :.detail content)))

(fc/defsc NodeDetails
  [this {::pcp/keys [node-id source-for-attrs requires input foreign-ast
                     after-nodes run-next]
         ::pc/keys  [sym node-resolver-error node-resolver-success]
         :as        node}]
  {:pre-merge (fn [{:keys [current-normalized data-tree]}]
                (merge {::pcp/node-id (random-uuid)} current-normalized data-tree))
   :ident     ::pcp/node-id
   :query     [::pcp/node-id
               ::pcp/run-and
               ::pcp/run-or
               ::pcp/source-for-attrs
               ::pcp/after-nodes
               ::pcp/run-next
               ::pcp/requires
               ::pcp/input
               ::pcp/foreign-ast
               ::pc/node-resolver-error
               ::pc/node-resolver-success
               ::pc/sym]
   :css       [[:.container {:padding  "14px"
                             :overflow "auto"
                             :flex     "1"}]
               [:.detail {:margin-bottom "8px"}]
               [:.code {:font-family "monospace"
                        :white-space "pre"}]
               [:.title {:font-weight "bold"
                         :text-align  "center"}]
               [:.title-and {:text-transform "uppercase"}]
               [:.title-or {:text-transform "uppercase"}]
               [:.label {:font-weight   "bold"
                         :margin-bottom "5px"}]]}
  (dom/div :.container
    (case (pcp/node-kind node)
      ::pcp/node-resolver
      (dom/div :.title.title-resolver (str sym))

      ::pcp/node-and
      (dom/div :.title.title-and "And")

      ::pcp/node-or
      (dom/div :.title.title-or "Or")

      ::pcp/node-unknown
      "UNKNOWN")

    (if node-id
      (detail-info "Node ID" (str node-id)))

    (if-let [branches (pcp/node-branches node)]
      (detail-info "Branches" (dom/div :.code (pvh/pprint-str branches))))

    (if (seq after-nodes)
      (detail-info "After Nodes" (dom/div :.code (pvh/pprint-str after-nodes))))

    (if run-next
      (detail-info "Run Next" (dom/div :.code (pvh/pprint-str run-next))))

    (if source-for-attrs
      (detail-info "Source for attributes" (dom/div :.code (pvh/pprint-str source-for-attrs))))

    (if requires
      (detail-info "Requires" (dom/div :.code (pvh/pprint-str requires))))

    (if (seq input)
      (detail-info "Input"
        (dom/div :.code
          (pvh/pprint-str (or (::pc/resolver-call-input node-resolver-success)
                              (::pc/resolver-call-input node-resolver-error)
                              input)))))

    (if foreign-ast
      (detail-info "Foreign Query" (dom/div :.code (pvh/pprint-str (eql/ast->query foreign-ast)))))

    (if node-resolver-success
      (detail-info "Response" (dom/div :.code (pvh/pprint-str (::pc/resolver-response node-resolver-success)))))

    (if node-resolver-error
      (detail-info "Error" (dom/div :.code (pvh/pprint-str (::pc/resolver-error node-resolver-error)))))))

(def node-details-ui (fc/factory NodeDetails {:keyfn ::pcp/node-id}))

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
                (pcp/assoc-node node-id ::y (+ node-half-size (* (+ node-size node-space) k)))
                (pcp/assoc-node node-id ::width node-size)
                (pcp/assoc-node node-id ::height node-size)))
          g
          (map vector (sort-by branches-count #(compare %2 %) v) (range))))
      graph'
      depths)))

(defn events->plan [events]
  (let [events' (mapv #(some-> % (gobj/get "edn-original") deref) events)]
    (if-let [plan (->> (filter (comp #{"reader3-execute"} :event) events')
                       first :plan)]
      (update plan ::pcp/nodes
        (fn [nodes]
          (p.misc/map-vals pcp/integrate-node-log nodes))))))

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

(def kind-encoders
  {::pc/sym #(some-> % ::pc/sym name str)})

(defn render-node-value [{::keys [label-kind]
                          :or    {label-kind ::pc/sym}} node]
  (if-let [encoder (get kind-encoders label-kind)]
    (encoder node)
    (str (get node label-kind))))

(defn zoom-transform [element-fn]
  (let [svg-transform (pvh/use-d3-zoom element-fn)]
    (pvh/transform->css svg-transform)))

(fc/defsc QueryPlanViz
  [this {::pcp/keys [graph]
         ::keys     [on-click-node
                     selected-node-id
                     on-mouse-over-node
                     on-mouse-out-node]
         :or        {on-click-node      identity
                     on-mouse-over-node identity
                     on-mouse-out-node  identity}
         :as        props}]
  {:css
   [[:.container {:flex      1
                  :max-width "100%"
                  :overflow  "hidden"}
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

     [:.node {:fill "#ddd" :cursor "pointer"}
      [:&.node-and {:fill "#e8e840"}]
      [:&.node-or {:fill "#b3b3f5"}]
      [:&.node-dynamic {:fill "#828282"}]
      [:&.node-error {:fill "#ff5f5f"}]
      [:&.node-selected {:stroke       "#000"
                         :stroke-width "2px"}]]

     [:.node-untouched {:opacity "0.6"}]

     [:.line {:stroke         "#ef9d0e6b"
              :stroke-width   "2px"
              :fill           "none"
              :pointer-events "none"}
      [:&.line-focus {:stroke "#ef9d0eff"}]]

     [:.line-next {:stroke         "#0000006b"
                   :stroke-width   "2px"
                   :fill           "none"
                   :pointer-events "none"}
      [:&.line-focus {:stroke "#000"}]]

     [:.line-focus {:stroke-width "3px"}]

     [:.node-id {:text-anchor       "middle"
                 :dominant-baseline "central"
                 :font-size         "12px"
                 :pointer-events    "none"}]

     [:.label {:font-size   "11px"
               :text-align  "center"
               :margin      "0"
               :padding-top "6px"}]]]

   :use-hooks?
   true}
  (let [[focus set-focus-node!] (pvh/use-state nil)]
    (dom/div :.container
      (dom/svg {:width "100%" :height "100%" :ref #(gobj/set this "svg" %)}
        (dom/g {:transform (zoom-transform #(gobj/get this "svg"))}
          (for [{::keys     [x y width height]
                 ::pcp/keys [node-id run-next foreign-ast node-trace]
                 :as        node} (vals (::pcp/nodes graph))]
            (let [start {::x (+ x (/ width 2)) ::y (+ y height)}
                  cx    (+ x node-half-size)
                  cy    (+ y node-half-size)]
              (dom/g {:key     (str node-id)
                      :classes [(if-not (seq node-trace) :.node-untouched)]}
                (dom/circle :.node {:classes     [(cond
                                                    (::pcp/run-and node)
                                                    :.node-and
                                                    (::pcp/run-or node)
                                                    :.node-or)

                                                  (if foreign-ast :.node-dynamic)

                                                  (if (::pc/node-resolver-error node)
                                                    :.node-error)

                                                  (if (= selected-node-id node-id)
                                                    :.node-selected)]
                                    :cx          cx
                                    :cy          cy
                                    :r           node-half-size
                                    :onClick     #(on-click-node % node)
                                    :onMouseOver #(do
                                                    (on-mouse-over-node % node)
                                                    (set-focus-node! node-id))
                                    :onMouseOut  #(do
                                                    (on-mouse-out-node % node)
                                                    (set-focus-node! nil))})
                (dom/foreignObject {:x      (- x node-size)
                                    :y      (+ y node-size)
                                    :width  (* node-size 3)
                                    :height node-space}
                  (dom/p :.label
                    (render-node-value props node)))

                (dom/text :.node-id {:x      cx
                                     :y      cy
                                     :width  node-size
                                     :height node-size}
                  (str node-id))

                (for [next-node (mapv #(pcp/get-node graph %) (pcp/node-branches node))]
                  (dom/path :.line {:classes [(if (contains? #{node-id (::pcp/node-id next-node)} focus) :.line-focus)]
                                    :d       (create-path-curve start {::x (+ (::x next-node) (/ (::width next-node) 2)) ::y (::y next-node)})
                                    :key     (str node-id "->" (::pcp/node-id next-node))}))

                (if-let [next-node (pcp/get-node graph run-next)]
                  (dom/path :.line-next {:classes [(if (contains? #{node-id (::pcp/node-id next-node)} focus) :.line-focus)]
                                         :d       (create-path-curve start {::x (+ (::x next-node) (/ (::width next-node) 2)) ::y (::y next-node)})
                                         :key     (str node-id "->" (::pcp/node-id next-node))}))))))))))

(def query-plan-viz (fc/computed-factory QueryPlanViz))

(fm/defmutation set-plan-view-graph [{::pcp/keys [graph]}]
  (action [{:keys [state ref]}]
    (swap! state update-in ref assoc
      ::pcp/graph (some-> graph (layout-graph))
      :ui/node-details nil)))

(defn set-plan-view-graph! [app {::keys [id]} graph]
  (fc/transact! app [(set-plan-view-graph {::pcp/graph graph})]
    {:ref [::id id]}))

(fc/defsc PlanViewWithDetails
  [this {::pcp/keys [graph]
         :ui/keys   [label-kind node-details]
         :as        props}]
  {:pre-merge   (fn [{:keys [current-normalized data-tree]}]
                  (merge {::id           (random-uuid)
                          :ui/label-kind ::pc/sym}
                    current-normalized data-tree))
   :ident       ::id
   :query       [::id ::pcp/graph :ui/label-kind :ui/node-details]
   :css-include [QueryPlanViz NodeDetails]
   :use-hooks?  true}
  (let [details-width (pvh/use-persistent-state ::details-width 400)]
    (ui/row (ui/gc :.flex :.no-scrollbars)
      (ui/column {:classes [(ui/css :.flex)]}
        (ui/toolbar {}
          (dom/label
            (dom/span {:style {:margin "0 5px"}} "Label kind:")
            (ui/dom-select {:value    label-kind
                            :onChange #(fm/set-value! this :ui/label-kind %2)}
              (ui/dom-option {:value ::pc/sym} "Node name")
              (ui/dom-option {:value ::pcp/source-for-attrs} "Attribute source"))))

        (query-plan-viz
          {::pcp/graph
           graph

           ::selected-node-id
           (::pcp/node-id node-details)

           ::label-kind
           label-kind

           ::on-click-node
           (fn [e node]
             (js/console.log "NODE" node)
             (fm/set-value! this :ui/node-details
               (if (= node-details node)
                 nil
                 node)))}))

      (if node-details
        (fc/fragment
          (ui/drag-resize
            {:state     details-width
             :direction "right"})

          (dom/div {:style   {:width (str @details-width "px")}
                    :classes [(ui/css :.scrollbars)]}
            (node-details-ui node-details)))))))

(def plan-view-with-details (fc/factory PlanViewWithDetails {:keyfn ::id}))
