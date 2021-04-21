(ns com.wsscode.pathom3.viz.plan
  (:require
    ["cytoscape" :as cytoscape]
    ["cytoscape-dagre" :as cytoscape-dagre]
    [clojure.datafy :as d]
    [com.wsscode.pathom3.connect.indexes :as pci]
    [com.wsscode.pathom3.connect.operation :as pco]
    [com.wsscode.pathom3.connect.planner :as pcp]
    [com.wsscode.pathom3.connect.runner :as pcr]
    [com.wsscode.pathom3.entity-tree :as p.ent]
    [com.wsscode.pathom3.interface.smart-map :as psm]
    [com.wsscode.pathom3.viz.ui :as ui]
    [edn-query-language.core :as eql]
    [goog.object :as gobj]
    [helix.core :as h]
    [helix.dom :as dom]
    [helix.hooks :as hooks]
    [com.wsscode.misc.coll :as coll]
    [clojure.string :as str]
    [com.wsscode.pathom.viz.fulcro]
    [com.wsscode.pathom.viz.ui.kit :as uip]
    [com.wsscode.pathom.viz.codemirror6 :as cm6]
    [com.wsscode.pathom.viz.helpers :as pvh]
    [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
    [com.wsscode.pathom3.interface.eql :as p.eql]))

(.use cytoscape cytoscape-dagre)

(pco/defresolver node-type [env _]
  {::pco/input [::pcp/node-id]}
  {::pcp/node-type (pcp/node-kind (p.ent/entity env))})

(pco/defresolver node-label
  [{::pcp/keys [node-type]
    ::pco/keys [op-name]}]
  {::pco/input [(pco/? ::pco/op-name) ::pcp/node-type]}
  {::node-label
   (case node-type
     ::pcp/node-and "AND"
     ::pcp/node-or "OR"
     ::pcp/node-resolver (name op-name))})

(pco/defresolver node-type-class [{::pcp/keys [node-type]}]
  {::node-type-class
   (case node-type
     ::pcp/node-and "node-and"
     ::pcp/node-or "node-or"
     ::pcp/node-resolver "node-resolver")})

(pco/defresolver node-output-state
  [{::pcr/keys [node-error
                node-resolver-output
                node-resolver-output-shape]
    :as        node}]
  {::pco/input
   [(pco/? ::pcr/node-error)
    (pco/? ::pcr/node-resolver-output)
    (pco/? ::pcr/node-resolver-output-shape)]}
  {::node-output-state
   (cond
     node-error
     ::node-state-error

     (or (and (contains? node ::pcr/node-resolver-output)
              (nil? node-resolver-output))
         (and (contains? node ::pcr/node-resolver-output-shape)
              (empty? node-resolver-output-shape)))
     ::node-state-empty

     (or node-resolver-output
         node-resolver-output-shape)
     ::node-state-success)})

(def node-extensions-registry
  [node-type
   node-label
   node-type-class
   node-output-state])

(defn smart-plan [plan]
  (-> (psm/smart-run-stats plan)
      (psm/sm-update-env pci/register node-extensions-registry)))

(def query-ast-resolvers
  [(pbir/single-attr-resolver ::eql/query :edn-query-language.ast/node eql/query->ast)
   (pbir/single-attr-resolver :edn-query-language.ast/node ::eql/query eql/ast->query)])

(def qt-env
  (pci/register query-ast-resolvers))

(defn ^:export compute-frames
  [env]
  (->> (p.eql/satisfy qt-env env [:edn-query-language.ast/node])
       (pcp/compute-plan-snapshots)
       (mapv smart-plan)))

(defn ^:export compute-plan-elements [{::pcp/keys [nodes root highlight-nodes highlight-styles]
                                       ::keys     [node-in-focus]}]
  (let [nodes'  (vals nodes)
        c-nodes (mapv
                  (fn [{::pcp/keys [node-id]
                        ::keys     [node-label node-type-class node-output-state]
                        :as        node}]
                    (let [hl-style   (get highlight-styles node-id)
                          node-color (case node-output-state
                                       ::node-state-error
                                       "node-error"

                                       ::node-state-empty
                                       "node-empty-output"

                                       ::node-state-success
                                       "node-success"

                                       nil)]
                      {:group   "nodes"
                       :data    {:id          (str node-id)
                                 :label       node-label
                                 :source-node (volatile! node)}
                       :classes (cond-> [node-type-class]
                                  node-color (conj node-color)
                                  (contains? highlight-nodes node-id) (conj (cond-> "node-highlight"
                                                                              hl-style (str "-" hl-style)))
                                  (= node-in-focus node-id) (conj "focus")
                                  (= root node-id) (conj "root"))}))
                  nodes')
        all     (into c-nodes
                      (mapcat
                        (fn [{::pcp/keys [node-id run-next]
                              :as        node}]
                          (cond->
                            (for [branch (pcp/node-branches node)]
                              {:group   "edges"
                               :data    {:id (str node-id "," branch ",B") :source node-id :target branch}
                               :classes ["branch"]})

                            run-next
                            (conj {:group   "edges"
                                   :data    {:id (str node-id "," run-next ",N") :source node-id :target run-next}
                                   :classes ["next"]}))))
                      nodes')]
    all))

(defn prepare-frames [snapshots]
  (into []
        (map
          (fn [snap]
            (let [smart-snap (smart-plan snap)]
              [smart-snap (compute-plan-elements smart-snap)])))
        snapshots))

(defn create-coll [^js cy elements]
  (.add (.collection cy) (into-array elements)))

(defn node-diff [^js cy new-els current-els]
  (let [current-els-ids   (into #{} (map #(.id %)) current-els)
        new-state-els-ids (into #{} (map #(-> % :data :id)) new-els)
        add-els           (remove #(contains? current-els-ids (-> % :data :id)) new-els)
        remove-els        (remove #(contains? new-state-els-ids (.id %)) current-els)]
    [add-els (create-coll cy remove-els)]))

(defn display-type->label [display-type]
  (case display-type
    ::display-type-node-id
    "data(id)"

    ::display-type-label
    "data(label)"

    "X"))

(def anim-duration 300)

(defn add-fade-in [^js cy elms]
  (-> (.add cy elms)
      (.style "opacity" 0)
      (.animate #js {:style    #js {:opacity 1}
                     :duration anim-duration
                     :easing   "ease-in-sine"})))

(defn remove-fade-out [^js cy coll]
  (-> coll
      (.animate #js {:style    #js {:opacity 0}
                     :duration anim-duration
                     :easing   "ease-in-sine"
                     :complete #(.remove cy coll)})))

(defn cytoscape-node-label-effect [cy-ref label-style]
  (hooks/use-effect [label-style]
    (some-> @cy-ref
            (.style)
            (.selector "node")
            (.style "label" label-style)
            (.update))))

(defn fit-node-and-neighbors [^js cy nodes node-id]
  (if node-id
    (js/setTimeout
      (fn []
        (when-let [{:keys [data]} (->> nodes
                                       (filter #(= (-> % :data :source-node deref ::pcp/node-id)
                                                   node-id))
                                       first)]
          (let [node      (-> data :source-node deref)
                neighbors (into #{(::pcp/node-id node)}
                                (filter some?)
                                (concat
                                  [(::pcp/run-next node)]
                                  (::pcp/node-parents node)
                                  (pcp/node-branches node)))
                query     (str/join ", "
                            (mapv #(str "[id=\"" % "\"]")
                              neighbors))]
            (-> cy
                (.animation #js {:fit #js {:eles (.nodes cy query)}} 150)
                (.play)))))
      500)))

(defn cytoscape-plan-view-effect [cy-ref container-ref elements {::keys [node-in-focus]}
                                  on-select-node]
  (let [{:strs [nodes edges]} (group-by :group elements)
        cy ^js @cy-ref]

    (hooks/use-effect [elements on-select-node]
      (if cy
        (let [[add-nodes remove-nodes] (node-diff cy nodes (.nodes cy))
              [add-edges remove-edges] (node-diff cy edges (.edges cy))
              remove-all (.add remove-nodes remove-edges)]
          (.batch cy
            (fn []
              (remove-fade-out cy remove-all)
              (doseq [{:keys [data classes]} nodes]
                (let [node-search (.nodes cy (str "[id=\"" (:id data) "\"]"))]
                  (when-let [node ^js (first node-search)]
                    (.data node "label" (:label data))
                    (.classes node (into-array classes))
                    (vreset! (gobj/get (.data node) "source-node")
                      @(:source-node data)))))
              (add-fade-in cy (clj->js add-nodes))
              (add-fade-in cy (clj->js add-edges))))
          (if (pos? (+ (count add-nodes) (count add-edges) (count remove-all)))
            (-> cy
                (.elements)
                (.difference remove-all)
                (.layout #js {:name              "dagre"
                              :rankDir           "LR"
                              :animate           true
                              :animationDuration anim-duration
                              :fit               false})
                (.run))))
        (let [cy (cytoscape
                   #js {:container @container-ref
                        :layout    #js {:name    "dagre"
                                        :rankDir "LR"
                                        ;:ready   #(fit-node-and-neighbors (gobj/get % "cy") nodes node-in-focus)
                                        }
                        :style     #js [#js {:selector "node"
                                             :style    #js {:text-valign         "center"
                                                            :transition-property "border-color border-width"
                                                            :transition-duration (str anim-duration "ms")}}
                                        #js {:selector "node.node-and"
                                             :style    #js {:background-color "#be8cd8"}}
                                        #js {:selector "node.node-or"
                                             :style    #js {:background-color "#17becf"}}
                                        #js {:selector "node.node-resolver"
                                             :style    #js {:background-color "#7f7f7f"}}
                                        #js {:selector "node.root"
                                             :style    #js {:border-width 3
                                                            :border-color "#000"}}
                                        #js {:selector "node.focus"
                                             :style    #js {:border-width 3
                                                            :border-color "#e00"}}
                                        #js {:selector "node.node-success"
                                             :style    #js {:background-color "#00cc00"}}
                                        #js {:selector "node.node-empty-output"
                                             :style    #js {:background-color "#cccc00"}}
                                        #js {:selector "node.node-error"
                                             :style    #js {:background-color "#ec6565"}}
                                        #js {:selector "node.node-highlight"
                                             :style    #js {:border-width 3
                                                            :border-color "#00aa00"}}
                                        #js {:selector "node.node-highlight-1"
                                             :style    #js {:border-width 3
                                                            :border-color "#0000aa"}}
                                        #js {:selector "edge"
                                             :style    #js {:curve-style        "bezier"
                                                            :width              2
                                                            :arrow-scale        0.8
                                                            :target-arrow-shape "triangle"}}
                                        #js {:selector "edge.branch"
                                             :style    #js {:line-color         "#ff7f0e"
                                                            :target-arrow-color "#ff7f0e"}}
                                        #js {:selector "edge.next"
                                             :style    #js {:line-color         "#000"
                                                            :target-arrow-color "#000"}}]
                        :elements  (clj->js elements)})]
          (.on cy "click" "node"
            (fn [e]
              (when-let [node-data (some-> e .-target (aget 0) (.data) (gobj/get "source-node") deref)]
                (if on-select-node (on-select-node (::pcp/node-id node-data)))
                (js/console.log (::pcp/node-id node-data)
                  (-> node-data
                      (psm/sm-touch! (vec (keys (d/datafy node-data))))
                      d/datafy
                      (->> (coll/remove-vals #{::pco/unknown-value})))))))
          (reset! cy-ref cy))))

    (hooks/use-effect [node-in-focus]
      (if cy
        (fit-node-and-neighbors cy nodes node-in-focus)))))

(h/defnc PlanGraphView [{:keys [elements run-stats display-type on-select-node]}]
  (let [elements'     (hooks/use-memo [(hash run-stats) (hash elements)]
                        (or elements (some-> run-stats smart-plan compute-plan-elements)))
        container-ref (hooks/use-ref nil)
        cy-ref        (hooks/use-ref nil)]
    (cytoscape-plan-view-effect cy-ref container-ref elements' run-stats on-select-node)
    (cytoscape-node-label-effect cy-ref (display-type->label display-type))
    (dom/div {:style {:flex     "1"
                      :overflow "hidden"}
              :ref   container-ref})))

(h/defnc PlanGraphWithNodeDetails [{:keys [run-stats display-type
                                           on-select-node]}]
  (let [selected-node (get run-stats ::node-in-focus)
        details-size  (pvh/use-persistent-state ::node-details-size 200)]
    (dom/div {:class "flex-row flex-1 overflow-hidden"}
      (dom/div {:style (cond-> {:width   (str @details-size "px")
                                :display "flex"}
                         (not selected-node)
                         (assoc :flex "1"))
                :class "min-w-40"}
        (h/$ PlanGraphView {:run-stats      run-stats
                            :display-type   display-type
                            :on-select-node on-select-node}))

      (if selected-node
        (let [run-stats (smart-plan run-stats)]
          (h/<>
            (uip/drag-resize {:state details-size :direction "left"})
            (dom/div {:class "flex-col flex-1 overflow-hidden min-w-40"}
              (uip/section-header {}
                (dom/div {:class "flex-row items-center"}
                  "Node Details"
                  (uip/button {:classes ["ml-4"] :onClick #(on-select-node 0)} "View Graph Data")
                  (dom/div {:style {:flex "1"}})
                  (uip/button {:onClick #(on-select-node nil)} "Hide Details")))
              (cm6/clojure-read
                (if (zero? selected-node)
                  (psm/sm-entity run-stats)
                  (some-> (get-in run-stats [::pcp/nodes selected-node])
                          (psm/sm-touch!
                            [::pcr/node-run-duration-ms])
                          (psm/sm-entity)))))))))))

(h/defnc ^:export PlanSnapshots [{:keys [frames display]}]
  {:wrap [(h/memo =)]}
  (let [[current-frame :as frame-state] (hooks/use-state (dec (count frames)))
        [{::pcp/keys [snapshot-message] :as graph} elements] (get frames current-frame)
        [display-type :as display-type-state] (hooks/use-state (or display ::display-type-node-id))]
    (dom/div {:style {:width            "100%"
                      :height           "100%"
                      :display          "flex"
                      :flex-direction   "column"
                      :background-color "#eee"
                      :color            "#000"}}
      (dom/div {:class "flex-row items-center mb-1 space-x-2"}
        (ui/dom-select {::ui/options [[::display-type-node-id "Node ID"]
                                      [::display-type-label "Label"]]
                        ::ui/state   display-type-state})
        (dom/div {:class ["flex-1"]} (str snapshot-message))
        (uip/button {:onClick #(js/console.log (-> graph psm/sm-env p.ent/entity))} "Log Graph"))
      (dom/div {:class "flex-row flex-1 overflow-hidden"}
        (uip/native-select
          (ui/dom-props {::ui/state (ui/state-hook-serialize frame-state)
                         :classes   ["bg-none w-52"]
                         :style     {:paddingRight "0.5rem"}
                         :size      2})
          (for [[i {::pcp/keys [snapshot-message]}] (->> frames (map first) (map vector (range)))]
            (dom/option {:key i :value i} snapshot-message)))
        (h/$ PlanGraphView {:elements elements :display-type display-type})))))
