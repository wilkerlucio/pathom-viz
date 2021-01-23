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
    [clojure.string :as str]))

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

(def node-extensions-registry [node-type node-label node-type-class])

(defn smart-plan [plan]
  (-> (psm/smart-run-stats plan)
      (psm/sm-update-env assoc ::pcr/resolver-cache* nil)
      (psm/sm-update-env pci/register node-extensions-registry)))

(defn ^:export compute-frames
  [{::pci/keys [index-oir]
    ::pcp/keys [available-data]
    ::eql/keys [query]}]
  (let [snapshots* (atom [])
        graph      (pcp/compute-run-graph
                     (cond-> {::pci/index-oir              index-oir
                              ::pcp/snapshots*             snapshots*
                              :edn-query-language.ast/node (eql/query->ast query)}
                       available-data
                       (assoc ::pcp/available-data available-data)))
        frames     (-> (mapv smart-plan @snapshots*)
                       (conj (smart-plan (assoc graph ::pcp/snapshot-message "Completed graph."))))]
    frames))

(defn ^:export compute-plan-elements [{::pcp/keys [nodes root highlight-nodes highlight-styles]
                                       ::keys     [node-in-focus] :as data}]
  (let [nodes'  (vals nodes)
        c-nodes (mapv
                  (fn [{::pcp/keys [node-id]
                        ::pcr/keys [node-resolver-output
                                    node-resolver-output-shape
                                    node-error]
                        ::keys     [node-label node-type-class]
                        :as        node}]
                    (let [hl-style   (get highlight-styles node-id)
                          node-color (cond
                                       node-error
                                       "node-error"

                                       (or (and (contains? node ::pcr/node-resolver-output)
                                                (nil? node-resolver-output))
                                           (and (contains? node ::pcr/node-resolver-output-shape)
                                                (empty? node-resolver-output-shape)))
                                       "node-empty-output"

                                       (or node-resolver-output
                                           node-resolver-output-shape)
                                       "node-success")]
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
            (.animation #js {:fit #js {:eles (.nodes cy query)}} 50)
            (.play))))))

(defn cytoscape-plan-view-effect [cy-ref container-ref elements {::keys [node-in-focus]}]
  (hooks/use-effect [elements node-in-focus]
    (if @cy-ref
      (let [cy         ^js @cy-ref
            {:strs [nodes edges]} (group-by :group elements)
            [add-nodes remove-nodes] (node-diff cy nodes (.nodes cy))
            [add-edges remove-edges] (node-diff cy edges (.edges cy))
            remove-all (.add remove-nodes remove-edges)]
        (.batch cy
          (fn []
            (remove-fade-out cy remove-all)
            (doseq [{:keys [data classes]} nodes]
              (let [node-search (.nodes cy (str "[id=\"" (:id data) "\"]"))]
                (when-let [node ^js (first node-search)]
                  (.classes node (into-array classes))
                  (vreset! (gobj/get (.data node) "source-node")
                    @(:source-node data)))))
            (add-fade-in cy (clj->js add-nodes))
            (add-fade-in cy (clj->js add-edges))))
        (if (zero? (+ (count remove-all) (count add-nodes) (count add-edges)))
          (fit-node-and-neighbors cy nodes node-in-focus)
          (-> cy
              (.elements)
              (.difference remove-all)
              (.layout #js {:name              "dagre"
                            :rankDir           "LR"
                            :animate           true
                            :animationDuration anim-duration
                            :fit               false
                            :ready             #(fit-node-and-neighbors cy nodes node-in-focus)})
              (.run))))
      (let [cy (cytoscape
                 #js {:container @container-ref
                      :layout    #js {:name    "dagre"
                                      :rankDir "LR"}
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
            (if-let [node-data (some-> e .-target (aget 0) (.data) (gobj/get "source-node") deref)]
              (js/console.log (::pcp/node-id node-data)
                (-> node-data
                    (psm/sm-touch! (vec (keys (d/datafy node-data))))
                    d/datafy
                    (->> (coll/remove-vals #{::pco/unknown-value})))))))
        (reset! cy-ref cy)))))

(h/defnc PlanGraphView [{:keys [elements run-stats display-type]}]
  (let [elements'     (hooks/use-memo [run-stats elements]
                        (or elements (some-> run-stats smart-plan compute-plan-elements)))
        container-ref (hooks/use-ref nil)
        cy-ref        (hooks/use-ref nil)]
    (cytoscape-plan-view-effect cy-ref container-ref elements' run-stats)
    (cytoscape-node-label-effect cy-ref (display-type->label display-type))
    (dom/div {:style {:flex     "1"
                      :overflow "hidden"}
              :ref   container-ref})))

(h/defnc ^:export PlanCytoscape [{:keys [frames display]}]
  (let [[current-frame :as frame-state] (hooks/use-state (dec (count frames)))
        [{::pcp/keys [snapshot-message] :as graph} elements] (get frames current-frame)
        [display-type :as display-type-state] (hooks/use-state (or display ::display-type-node-id))]
    (dom/div {:style {:width            "100%"
                      :height           "100%"
                      :display          "flex"
                      :flex-direction   "column"
                      :background-color "#eee"
                      :color            "#000"}}
      (dom/div {:style {:display     "flex"
                        :align-items "center"}}
        (ui/dom-select {::ui/options [[::display-type-node-id "Node ID"]
                                      [::display-type-label "Label"]]
                        ::ui/state   display-type-state})
        (dom/div {:style {:margin-left "10px"}} (str snapshot-message))
        (dom/button {:on-click #(js/console.log (-> graph psm/sm-env p.ent/entity))} "Log Graph"))
      (dom/div {:style {:flex     "1"
                        :overflow "hidden"
                        :display  "flex"}}
        (dom/select {:style {:width "160px"} :size 2
                     :&     (ui/dom-props {::ui/state (ui/state-hook-serialize frame-state)})}
          (for [[i {::pcp/keys [snapshot-message]}] (->> frames (map first) (map vector (range)))]
            (dom/option {:key i :value i} snapshot-message)))
        (h/$ PlanGraphView {:elements elements :display-type display-type})))))
