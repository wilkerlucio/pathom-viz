(ns com.wsscode.pathom.viz.index-explorer
  (:require ["./d3-attribute-graph" :as d3attr]
            ["./detect-element-size" :refer [addResizeListener]]
            [cljs.reader :refer [read-string]]
            [cljs.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.test.check.generators :as gen]
            [com.wsscode.fuzzy :as fuzzy]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.viz.helpers :as h]
            [com.wsscode.pathom.viz.ui.expandable-tree :as ex-tree]
            [com.wsscode.pathom.viz.ui.kit :as ui]
            [com.wsscode.pathom.viz.ui.context :as uic]
            [com.wsscode.spec-inspec :as si]
            [edn-query-language.core :as eql]
            [fulcro-css.css :as css]
            [fulcro.client.localized-dom :as dom]
            [fulcro.client.mutations :as fm]
            [fulcro.client.primitives :as fp]
            [ghostwheel.core :as g :refer [>defn >defn- >fdef => | <- ?]]
            [goog.object :as gobj]))

; region specs

(s/def ::weight nat-int?)
(s/def ::reach nat-int?)
(s/def ::resolvers ::pc/attributes-set)

(s/def :com.wsscode.pathom.viz.index-explorer.attribute-node/attribute string?)
(s/def :com.wsscode.pathom.viz.index-explorer.attribute-node/multiNode boolean?)
(s/def :com.wsscode.pathom.viz.index-explorer.attribute-node/mainNode boolean?)
(s/def :com.wsscode.pathom.viz.index-explorer.attribute-node/weight ::weight)
(s/def :com.wsscode.pathom.viz.index-explorer.attribute-node/reach ::reach)
(s/def :com.wsscode.pathom.viz.index-explorer.attribute-node/radius (s/and double? pos?))

(s/def ::attribute-node
  (s/keys :req-un [:com.wsscode.pathom.viz.index-explorer.attribute-node/attribute
                   :com.wsscode.pathom.viz.index-explorer.attribute-node/multiNode
                   :com.wsscode.pathom.viz.index-explorer.attribute-node/mainNode
                   :com.wsscode.pathom.viz.index-explorer.attribute-node/weight
                   :com.wsscode.pathom.viz.index-explorer.attribute-node/reach
                   :com.wsscode.pathom.viz.index-explorer.attribute-node/radius]))

(s/def :com.wsscode.pathom.viz.index-explorer.attribute-link/source string?)
(s/def :com.wsscode.pathom.viz.index-explorer.attribute-link/weight ::weight)
(s/def :com.wsscode.pathom.viz.index-explorer.attribute-link/resolvers string?)
(s/def :com.wsscode.pathom.viz.index-explorer.attribute-link/target string?)
(s/def :com.wsscode.pathom.viz.index-explorer.attribute-link/deep boolean?)

(s/def ::attribute-link
  (s/keys :req-un [:com.wsscode.pathom.viz.index-explorer.attribute-link/source
                   :com.wsscode.pathom.viz.index-explorer.attribute-link/weight
                   :com.wsscode.pathom.viz.index-explorer.attribute-link/resolvers
                   :com.wsscode.pathom.viz.index-explorer.attribute-link/target
                   :com.wsscode.pathom.viz.index-explorer.attribute-link/deep]))

(s/def :com.wsscode.pathom.viz.index-explorer.attribute-graph/nodes (s/coll-of ::attribute-node))
(s/def :com.wsscode.pathom.viz.index-explorer.attribute-graph/links (s/coll-of ::attribute-link))

(s/def ::attribute-graph
  (s/keys :req-un [:com.wsscode.pathom.viz.index-explorer.attribute-graph/nodes
                   :com.wsscode.pathom.viz.index-explorer.attribute-graph/links]))

(s/def ::maybe-nested-input (s/or :direct set? :nested vector?))

; endregion

; region view helpers

(def ExtensionContext (uic/new-context))

(>defn call-graph-comm [comp f k]
  [any? string? any? => any?]
  (if-let [settings (or (some-> (fp/get-state comp :graph-comm) deref)
                        (some-> comp fp/props fp/get-computed ::graph-comm deref))]
    ((gobj/get settings f) (str k))))

(defn attribute-graph-events [this k]
  (let [on-select-attribute (-> this fp/props fp/get-computed ::on-select-attribute)]
    {:onClick      #(on-select-attribute k)
     :onMouseEnter #(call-graph-comm this "highlightNode" k)
     :onMouseLeave #(call-graph-comm this "unhighlightNode" k)}))

(defn resolver-graph-events [this k]
  (let [on-select-resolver (-> this fp/props fp/get-computed ::on-select-resolver)]
    {:onClick      #(on-select-resolver k)
     :onMouseEnter #(call-graph-comm this "highlightEdge" k)
     :onMouseLeave #(call-graph-comm this "unhighlightEdge" k)}))

(def color-attribute "#9a45b1")

(fp/defsc AttributeLink
  [this {::pc/keys [attribute] ::ui/keys [render] :as props}]
  {:css [[:.container {:cursor      "pointer"
                       :color       color-attribute
                       :font-size   "14px"
                       :line-height "1.4em"}
          ui/text-base]]}
  (dom/div :.container (ui/dom-props (merge (attribute-graph-events this attribute) props))
    (if render (render props) (pr-str attribute))))

(def attribute-link (fp/computed-factory AttributeLink {:keyfn (comp pr-str ::pc/attribute)}))

(def color-resolver "#467cb7")

(fp/defsc ResolverLink
  [this {::pc/keys [sym] ::ui/keys [render] :as props}]
  {:css [[:.container {:cursor      "pointer"
                       :color       color-resolver
                       :font-size   "14px"
                       :line-height "1.4em"}
          ui/text-base]]}
  (dom/div :.container (ui/dom-props (merge (resolver-graph-events this sym) props))
    (if render (render props) (pr-str sym))))

(def resolver-link (fp/computed-factory ResolverLink {:keyfn (comp pr-str ::pc/sym)}))

(def color-mutation "#ef6c00")

(fp/defsc MutationLink
  [this {::pc/keys [sym] ::ui/keys [render] :as props}]
  {:css [[:.container {:cursor      "pointer"
                       :color       color-mutation
                       :font-size   "14px"
                       :line-height "1.4em"}
          ui/text-base]]}
  (let [on-select-mutation (-> this fp/props fp/get-computed ::on-select-mutation)]
    (dom/div :.container (ui/dom-props (merge {:onClick #(on-select-mutation sym)} props))
      (if render (render props) (pr-str sym)))))

(def mutation-link (fp/computed-factory MutationLink {:keyfn (comp pr-str ::pc/sym)}))

;endregion

;; Main components

(>defn node-radius
  [{::keys [weight reach]}]
  [(s/keys :req [::weight ::reach]) => double?]
  (js/Math.round
    (+
      (js/Math.sqrt (+ (or weight 1) 2))
      (js/Math.sqrt (+ (or reach 1) 1)))))

(>defn attribute->node
  [{::pc/keys [attribute]
    ::keys    [weight reach center?]
    :as       attr}]
  [(s/keys :req [::pc/attribute ::weight ::reach] :opt [::center?])
   => ::attribute-node]
  {:attribute (pr-str attribute)
   :multiNode (set? attribute)
   :mainNode  (boolean center?)
   :weight    weight
   :reach     reach
   :radius    (node-radius attr)})

(>defn direct-input? [input] [::maybe-nested-input => boolean?]
  (set? input))

(>defn nested? [input] [any? => boolean?] (vector? input))

(>defn single-input [input] [::maybe-nested-input => (? ::p/attribute)]
  (let [input (if (nested? input) (first input) input)]
    (or (and (= 1 (count input)) (first input))
        nil)))

(>defn global-input? [input] [::maybe-nested-input => boolean?]
  (and (direct-input? input) (empty? input)))

(>defn compute-nodes-links [{::keys [attributes]}]
  [(s/keys :req [::attributes]) => ::attribute-graph]
  (let [attributes (filter ::pc/attribute attributes)
        index      (h/index-by ::pc/attribute attributes)]
    {:nodes (into [] (map attribute->node) attributes)
     :links (mapcat
              (fn [{::pc/keys [attribute attr-provides]}]
                (let [attr-str (pr-str attribute)]
                  (let [res (-> []
                                (into
                                  (keep (fn [[provided resolvers]]
                                          (let [nested?   (nested? provided)
                                                provided' (if nested?
                                                            (peek provided)
                                                            provided)]
                                            (when (and (contains? index provided')
                                                       (not= attribute provided'))
                                              {:source    attr-str
                                               :weight    (count resolvers)
                                               :resolvers (str/join "\n" resolvers)
                                               :target    (pr-str provided')
                                               :deep      nested?}))))
                                  attr-provides))]
                    res)))
              attributes)}))

(defn render-attribute-graph [this]
  (let [{::keys [on-show-details on-click-edge graph-comm] :as props} (-> this fp/props)
        on-show-details (or on-show-details identity)
        on-click-edge   (or on-click-edge identity)
        current         (gobj/get this "renderedData")
        container       (gobj/get this "svgContainer")
        svg             (gobj/get this "svg")]
    (if current ((gobj/get current "dispose")))
    (gobj/set svg "innerHTML" "")
    (let [render-settings (d3attr/render svg
                            (clj->js {:svgWidth    (gobj/get container "clientWidth")
                                      :svgHeight   (gobj/get container "clientHeight")
                                      :data        (compute-nodes-links props)
                                      :showDetails (fn [attr d js]
                                                     (on-show-details (read-string attr) d js))
                                      :onClickEdge (fn [edge]
                                                     (let [resolvers (-> (str "#{" (gobj/get edge "resolvers") "}")
                                                                         (read-string))]
                                                       (on-click-edge {::resolvers resolvers})))}))]
      (if graph-comm (reset! graph-comm render-settings))
      (gobj/set this "renderedData" render-settings))))

(fp/defsc AttributeGraph
  [this {::keys []}]
  {:css
   [[:.container {:flex      1
                  :max-width "100%"}
     [:$pathom-viz-index-explorer-attr-node
      {:fill "#000A"}

      [:&$pathom-viz-index-explorer-attr-node-multi
       {:fill         "#00000021"
        :stroke       "#101010"
        :stroke-width "5px"}]

      [:&$pathom-viz-index-explorer-attr-node-main
       {:fill "#f9e943e3"}]

      [:&$pathom-viz-index-explorer-attr-node-highlight
       {:fill "#de2b34"}]]

     [:$pathom-viz-index-explorer-arrow-provides
      [:path
       {:fill "#666"}]]
     [:$pathom-viz-index-explorer-arrow-reaches
      [:path
       {:fill "#666"}]]

     [:$pathom-viz-index-explorer-attr-link
      {:stroke         "#999"
       :stroke-opacity "0.6"
       :stroke-width   "1.5px"
       :fill           "none"}

      [:&$pathom-viz-index-explorer-attr-link-focus-highlight
       {:stroke       "#4242e0db"
        :stroke-width "3px"
        :z-index      "10"}]

      [:&$pathom-viz-index-explorer-attr-link-target-highlight
       {:stroke       "#0c0"
        :stroke-width "3px"
        :z-index      "10"}]

      [:&$pathom-viz-index-explorer-attr-link-source-highlight
       {:stroke       "#cc1a9d"
        :stroke-width "2px"
        :z-index      "10"}]

      [:&$pathom-viz-index-explorer-attr-link-reach
       {}]
      [:&$pathom-viz-index-explorer-attr-link-deep
       {:stroke-dasharray "3px"}]]

     [:text {:font "bold 18px Verdana, Helvetica, Arial, sans-serif"}]]]

   :componentDidMount
   (fn []
     (render-attribute-graph this)
     #_(addResizeListener (gobj/get this "svgContainer") #(recompute-trace-size this)))

   :componentDidUpdate
   (fn [prev-props _]
     (when (not= prev-props (-> this fp/props))
       (render-attribute-graph this)))

   :componentWillUnmount
   (fn []
     (if-let [settings (gobj/get this "renderedData")]
       ((gobj/get settings "dispose"))))

   :componentDidCatch
   (fn [error info]
     (fp/set-state! this {::error-catch? true}))}
  (dom/div :.container {:ref #(gobj/set this "svgContainer" %)}
    (if (fp/get-state this ::error-catch?)
      (dom/div "Error rendering trace, check console for details")
      (dom/svg {:ref #(gobj/set this "svg" %)}))))

(def attribute-graph (fp/factory AttributeGraph))

(>defn pull-attr
  "Get attribute from index, remove provides when interconnections is falsy."
  [{::keys [attr-index interconnections?]} attr]
  [(s/keys :req [::attr-index] :opt [::interconnections?]) ::pc/attribute
   => (s/keys)]
  (cond-> (get attr-index attr)
    (false? interconnections?)
    (dissoc ::pc/attr-provides)))

(defn attribute-network*
  [{::keys [attr-depth attributes sub-index attr-index attr-visited
            direct-reaches? nested-reaches? direct-provides? nested-provides?]
    :or    {attr-depth       1
            direct-reaches?  true
            nested-reaches?  false
            direct-provides? true
            nested-provides? false
            sub-index        {}
            attr-visited     #{}}
    :as    options} source]
  (if (contains? attr-visited source)
    sub-index
    (let [index    (or attr-index (h/index-by ::pc/attribute attributes))
          base     (merge sub-index (select-keys index [source]))
          {::pc/keys [attr-reach-via attr-provides]} (get index source)
          options' (assoc options ::attr-index index
                                  ::attr-depth (dec attr-depth)
                                  ::attr-visited (conj attr-visited source))]
      (as-> base <>
        ; reach
        (reduce
          (fn [out input]
            (if (or (and direct-reaches? (direct-input? input))
                    (and nested-reaches? (nested? input)))
              (if-let [attr (single-input input)]
                (if (> attr-depth 1)
                  (attribute-network*
                    (assoc options' ::sub-index out)
                    attr)
                  (update out attr merge (pull-attr options' attr)))
                (let [input (if (vector? input) (first input) input)]
                  (update out input merge (pull-attr options' input))))
              out))
          <>
          (keys attr-reach-via))
        ; provides
        (reduce
          (fn [out attr]
            (cond
              (and direct-provides? (keyword? attr))
              (if (> attr-depth 1)
                (attribute-network*
                  (assoc options' ::sub-index out)
                  attr)
                (update out attr merge (pull-attr options' attr)))

              (and nested-provides? (nested? attr))
              (let [attr (peek attr)]
                (update out attr merge (pull-attr options' attr)))

              :else
              out))
          <>
          (keys attr-provides))))))

(defn attribute-network [options source]
  (-> (attribute-network* options source)
      (update source assoc ::center? true)
      (vals)))

(defn attr-path-key-root [x]
  (if (vector? x) (first x) x))

(>defn attr-provides->path-map [attr-provides]
  [::pc/attr-provides => ::h/path-map]
  (into {}
        (comp (map #(update % 0 (fn [x] (if (keyword? x) [x] x))))
              (map (fn [[path resolvers]]
                     (let [k (peek path)]
                       [path {:key k ::pc/sym-set resolvers}]))))
        attr-provides))

(>defn attr-provides->tree [attr-provides]
  [::pc/attr-provides => ::ex-tree/root]
  (-> attr-provides
      attr-provides->path-map
      h/path-map->tree))

(defn render-plugin-extension [this view]
  (let [plugins (-> (gobj/get this "context") ::plugins)
        data    (-> this fp/get-reconciler fp/app-state deref (get-in (fp/get-ident this)))]
    (for [{::keys [plugin-id] :as plugin} plugins
          :when (contains? plugin view)]
      (dom/div {:key (pr-str plugin-id)}
        ((get plugin view) data)))))

(fp/defsc AttributeInfoReachVia
  [this {::pc/keys [attr-reach-via]} computed]
  {:ident [::pc/attribute ::pc/attribute]
   :query [::pc/attribute ::pc/attr-reach-via]}
  (ui/panel {::ui/panel-title "Reach via"
             ::ui/panel-tag   (count attr-reach-via)}
    (let [nested-reaches? true]
      (for [[input v] (->> attr-reach-via
                           (group-by (comp attr-path-key-root first))
                           (sort-by (comp pr-str attr-path-key-root first)))
            :let [direct? (some (comp direct-input? first) v)]
            :when (or direct? nested-reaches?)]
        (dom/div {:key (pr-str input)}
          (attribute-link {::pc/attribute (cond-> input (= (count input) 1) first)
                           ::ui/render    #(pr-str input)
                           :style         (cond-> {} direct? (assoc :fontWeight "bold"))}
            computed)
          (if nested-reaches?
            (for [[path resolvers] (->> v
                                        (map #(update % 0 (fn [x] (if (set? x) [x] x))))
                                        (sort-by (comp #(update % 0 (comp vec sort)) first)))
                  :let [path' (next path)]
                  :when path']
              (dom/div {:key   (pr-str path)
                        :style {:marginLeft "10px"}}
                (for [[k i] (map vector path' (range))]
                  (attribute-link {::pc/attribute k
                                   :style         {:marginLeft (str (* i 10) "px")}}
                    computed))))))))))

(def attribute-info-reach-via (fp/computed-factory AttributeInfoReachVia))

(fp/defsc AttributeInfoMutationParamIn
  [this {::pc/keys [attr-mutation-param-in]} computed]
  {:ident [::pc/attribute ::pc/attribute]
   :query [::pc/attribute ::pc/attr-mutation-param-in]}
  (ui/panel {::ui/panel-title "Mutation Param In"
             ::ui/panel-tag   (count attr-mutation-param-in)}
    (for [sym (sort attr-mutation-param-in)]
      (mutation-link {::pc/sym sym} computed))))

(def attribute-info-mutation-param-in (fp/computed-factory AttributeInfoMutationParamIn))

(fp/defsc AttributeInfoMutationOutputIn
  [this {::pc/keys [attr-mutation-output-in]} computed]
  {:ident [::pc/attribute ::pc/attribute]
   :query [::pc/attribute ::pc/attr-mutation-output-in]}
  (ui/panel {::ui/panel-title "Mutation Output In"
             ::ui/panel-tag   (count attr-mutation-output-in)}
    (for [sym (sort attr-mutation-output-in)]
      (mutation-link {::pc/sym sym} computed))))

(def attribute-info-mutation-output-in (fp/computed-factory AttributeInfoMutationOutputIn))

(fp/defsc ExamplesPanel
  [this {::pc/keys [attribute]}]
  {:css [[:.examples {:font-family ui/font-code}]]}
  (ui/panel {::ui/panel-title
             (ui/row {:classes [:.center]}
               (dom/div "Examples")
               (dom/div (ui/gc :.flex))
               (ui/button {:onClick #(fp/set-state! this {:seed (rand)})} (dom/i {:classes ["fa" "fa-sync-alt"]})))}
    (dom/div :.examples
      (try
        (let [samples (distinct (gen/sample (s/gen attribute)))
              samples (try
                        (sort samples)
                        (catch :default _ samples))]
          (for [[i example] (map vector (range) samples)]
            (dom/div {:key (str "example-" i)} (pr-str example))))
        (catch :default _
          (dom/div "Error generating samples"))))))

(def examples-panel (fp/computed-factory ExamplesPanel))

(fp/defsc AttributeView
  [this {::pc/keys [attr-combinations attribute attr-reach-via attr-provides
                    attr-input-in attr-output-in
                    attr-mutation-param-in attr-mutation-output-in]
         ::keys    [attr-depth direct-reaches? nested-reaches? direct-provides?
                    nested-provides? interconnections? show-graph?]
         :>/keys   [reach-via mutation-param-in mutation-output-in]
         :ui/keys  [provides-tree provides-tree-source]}
   {::keys [on-select-attribute attributes]
    :as    computed}]
  {:pre-merge      (fn [{:keys [current-normalized data-tree]}]
                     (let [attr          (or (::pc/attribute data-tree)
                                             (::pc/attribute current-normalized))
                           attr-provides (or (::pc/attr-provides data-tree)
                                             (::pc/attr-provides current-normalized))]
                       (merge
                         {::attr-depth          1
                          ::direct-reaches?     true
                          ::nested-reaches?     false
                          ::direct-provides?    true
                          ::nested-provides?    false
                          ::interconnections?   true
                          ::show-graph?         false
                          :>/reach-via          {::pc/attribute attr}
                          :>/mutation-param-in  {::pc/attribute attr}
                          :>/mutation-output-in {::pc/attribute attr}
                          :ui/provides-tree     {}}
                         current-normalized
                         data-tree
                         (if attr-provides
                           {:ui/provides-tree-source (attr-provides->tree attr-provides)}))))
   :ident          [::pc/attribute ::pc/attribute]
   :query          [::pc/attribute ::attr-depth ::direct-reaches? ::nested-reaches?
                    ::pc/attr-combinations ::pc/attr-input-in ::pc/attr-output-in
                    ::pc/attr-mutation-param-in ::pc/attr-mutation-output-in
                    ::direct-provides? ::nested-provides? ::interconnections? ::show-graph? ::pc/attr-reach-via ::pc/attr-provides
                    {:>/reach-via (fp/get-query AttributeInfoReachVia)}
                    {:>/mutation-param-in (fp/get-query AttributeInfoMutationParamIn)}
                    {:>/mutation-output-in (fp/get-query AttributeInfoMutationOutputIn)}
                    {:ui/provides-tree (fp/get-query ex-tree/ExpandableTree)}
                    :ui/provides-tree-source]
   :css            [[:.container {:flex           "1"
                                  :flex-direction "column"
                                  :display        "flex"}]
                    [:.title {:color color-attribute}
                     ui/css-header
                     ui/text-base]
                    [:.toolbar {:display       "flex"
                                :align-items   "center"
                                :margin-bottom "16px"}]
                    [:.show-graph {:padding     "8px"
                                   :font-family ui/font-base
                                   :font-size   "1.1rem"}]
                    [:.graph-options {:font-size "0.9rem"}
                     [(ui/component-class ui/ToggleAction :.container)
                      {:margin "0 2px"}]]
                    [:.data-list {:white-space   "nowrap"
                                  ;:overflow      "auto"
                                  :box-sizing    "border-box"
                                  :width         "50%"
                                  :padding-right "12px"}]
                    [:.data-list-right {:white-space "nowrap"
                                        :width       "50%"
                                        :box-sizing  "border-box"
                                        :padding     "0 12px"}]
                    [:.graph {:height         "400px"
                              :width          "100%"
                              :display        "flex"
                              :align-items    "stretch"
                              :flex-direction "column"}]
                    [:.columns {:display "flex"
                                :flex    "1"}
                     [:text {:font "bold 16px Verdana, Helvetica, Arial, sans-serif"}]]
                    [:.links-container
                     [:&:hover
                      [:.links-display {:display "block"}]]]
                    [:.links-display {:display     "none"
                                      :margin-left "16px"}]]
   :css-include    [AttributeGraph ui/ToggleAction ExamplesPanel]
   :initLocalState (fn [] {:graph-comm      (atom nil)
                           :select-resolver (fn [{::keys [resolvers]}]
                                              (let [{::keys [on-select-resolver]} (fp/get-computed (fp/props this))]
                                                (on-select-resolver (first resolvers))))})}
  (let [computed (assoc computed ::graph-comm (fp/get-state this :graph-comm))]
    (dom/div :.container
      (dom/div :.toolbar
        (dom/h1 :.title (ui/gc :.flex) (pr-str attribute))

        (ui/toggle-action {::ui/active? show-graph?
                           :classes     (ui/ccss this :.show-graph)
                           :onClick     #(fm/set-value! this ::show-graph? (not show-graph?))}
          "Graph View"))

      (if show-graph?
        (ui/panel {::ui/panel-title (ui/row {:classes (ui/ccss this :.graph-options)}
                                      (ui/row {:classes [:.center]}
                                        (dom/label "Depth")
                                        (ui/number-input {:min      1
                                                          :value    attr-depth
                                                          :onChange #(fm/set-value! this ::attr-depth %2)}))
                                      (ui/toggle-action {::ui/active? direct-reaches?
                                                         :onClick     #(fm/set-value! this ::direct-reaches? (not direct-reaches?))}
                                        "Direct inputs")
                                      (ui/toggle-action {::ui/active? nested-reaches?
                                                         :onClick     #(fm/set-value! this ::nested-reaches? (not nested-reaches?))}
                                        "Nested inputs")
                                      (ui/toggle-action {::ui/active? direct-provides?
                                                         :onClick     #(fm/set-value! this ::direct-provides? (not direct-provides?))}
                                        "Direct outputs")
                                      (ui/toggle-action {::ui/active? nested-provides?
                                                         :onClick     #(fm/set-value! this ::nested-provides? (not nested-provides?))}
                                        "Nested outputs")
                                      (ui/toggle-action {::ui/active? interconnections?
                                                         :onClick     #(fm/set-value! this ::interconnections? (not interconnections?))}
                                        "Interconnections"))
                   ::ui/scrollbars? false}
          (dom/div :.graph
            (let [shared-options {::direct-reaches?   direct-reaches?
                                  ::nested-reaches?   nested-reaches?
                                  ::direct-provides?  direct-provides?
                                  ::nested-provides?  nested-provides?
                                  ::interconnections? interconnections?}]
              (attribute-graph
                (merge {::attributes      (attribute-network
                                            (merge {::attr-depth attr-depth
                                                    ::attr-index (h/index-by ::pc/attribute attributes)
                                                    ::attributes attributes}
                                              shared-options)
                                            attribute)
                        ::on-show-details on-select-attribute
                        ::on-click-edge   (fp/get-state this :select-resolver)
                        ::graph-comm      (fp/get-state this :graph-comm)}
                  shared-options))))))

      (dom/div :.columns$scrollbars
        (dom/div :.data-list
          (if (seq attr-reach-via)
            (attribute-info-reach-via reach-via computed))

          (if (seq attr-output-in)
            (ui/panel {::ui/panel-title "Output In"
                       ::ui/panel-tag   (count attr-output-in)}
              (for [resolver (sort attr-output-in)]
                (resolver-link {::pc/sym resolver} computed))))

          (if (seq attr-combinations)
            (ui/panel {::ui/panel-title "Input Combinations"
                       ::ui/panel-tag   (count attr-combinations)}
              (for [input (sort-by (comp vec sort) h/vector-compare (map #(into (sorted-set) %) attr-combinations))]
                (attribute-link {::pc/attribute input} computed))))

          (if (seq attr-mutation-param-in)
            (attribute-info-mutation-param-in mutation-param-in computed))

          (if (seq attr-mutation-output-in)
            (attribute-info-mutation-output-in mutation-output-in computed))

          (if-let [form (si/safe-form attribute)]
            (fp/fragment
              (ui/panel {::ui/panel-title "Spec"}
                (pr-str form))

              (examples-panel {::pc/attribute attribute} computed)))

          (render-plugin-extension this ::plugin-render-to-attr-left-menu))

        (dom/div :.data-list-right
          (if (seq attr-provides)
            (ui/panel {::ui/panel-title "Provides"
                       ::ui/panel-tag   (count attr-provides)}
              (ex-tree/expandable-tree provides-tree
                {::ex-tree/root    provides-tree-source
                 ::ex-tree/render  (fn [{:keys [key] ::pc/keys [sym-set]}]
                                     (ui/column {:classes (ui/ccss this :.links-container)}
                                       (attribute-link {::pc/attribute key} computed)
                                       (dom/div {:classes (ui/ccss this :.links-display)}
                                         (for [sym (sort sym-set)]
                                           (resolver-link {::pc/sym sym} computed)))))
                 ::ex-tree/sort-by :key})))

          (if (seq attr-input-in)
            (ui/panel {::ui/panel-title "Input In"
                       ::ui/panel-tag   (count attr-input-in)}
              (for [resolver (sort attr-input-in)]
                (resolver-link {::pc/sym resolver} computed)))))))))

(gobj/set AttributeView "contextType" ExtensionContext)

(def attribute-view (fp/computed-factory AttributeView {:keyfn ::pc/attribute}))

(>defn out-all-attributes [{:keys [children]} input]
  [:edn-query-language.ast/node ::pc/input
   => (s/coll-of ::p/attribute :kind set?)]
  (reduce
    (fn [attrs {:keys [key children] :as node}]
      (cond-> (if (contains? input key) attrs (conj attrs key))
        children
        (into (out-all-attributes node input))))
    #{}
    children))

(fp/defsc ResolverView
  [this {::pc/keys [sym input output batch?]
         :ui/keys  [output-tree]}
   {::keys [on-select-attribute attributes] :as computed}
   css]
  {:pre-merge      (fn [{:keys [current-normalized data-tree]}]
                     (merge
                       {:ui/output-tree {}}
                       current-normalized
                       data-tree))
   :ident          [::pc/sym ::pc/sym]
   :query          [::pc/sym ::pc/input ::pc/output ::pc/batch?
                    {:ui/output-tree (fp/get-query ex-tree/ExpandableTree)}]
   :css            [[:.title {:color color-resolver}
                     ui/css-header
                     ui/text-base]
                    [:.menu {:white-space   "nowrap"
                             :padding-right "12px"
                             :overflow      "auto"}]]
   :initLocalState (fn [] {:graph-comm      (atom nil)
                           :select-resolver (fn [{::keys [resolvers]}]
                                              (let [{::keys [on-select-resolver]} (fp/get-computed (fp/props this))]
                                                (on-select-resolver (first resolvers))))
                           :render          (fn [{:keys [key]}]
                                              (let [computed (-> this fp/props fp/get-computed)
                                                    computed (assoc computed ::graph-comm (fp/get-state this :graph-comm))]
                                                (attribute-link {::pc/attribute key} computed)))})}
  (let [input'   (if (= 1 (count input)) (first input) input)
        computed (assoc computed ::graph-comm (fp/get-state this :graph-comm))]
    (ui/column (ui/gc :.flex)
      (dom/h1 :.title (str sym))
      (ui/row (ui/gc :.flex :.no-scrollbars)
        (dom/div :.menu
          (if batch?
            (ui/panel {::ui/panel-title "Batch"}
              "True"))

          (if input
            (ui/panel {::ui/panel-title "Input"}
              (attribute-link {::pc/attribute input'} computed)))

          (if output
            (ui/panel {::ui/panel-title "Output"}
              (ex-tree/expandable-tree output-tree
                {::ex-tree/root    (-> (eql/query->ast output)
                                       (update :children
                                         #(remove (comp input :key) %)))
                 ::ex-tree/render  (fp/get-state this :render)
                 ::ex-tree/sort-by :key})))

          (render-plugin-extension this ::plugin-render-to-resolver-menu))

        (if input
          (let [resolver-attrs (conj (out-all-attributes (->> output eql/query->ast) input) input')
                attrs          (-> (h/index-by ::pc/attribute attributes)
                                   (select-keys resolver-attrs)
                                   (update input' assoc ::center? true)
                                   vals)]
            (attribute-graph {::attributes      attrs
                              ::graph-comm      (fp/get-state this :graph-comm)
                              ::on-show-details on-select-attribute
                              ::on-click-edge   (fp/get-state this :select-resolver)})))))))

(gobj/set ResolverView "contextType" ExtensionContext)

(def resolver-view (fp/factory ResolverView {:keyfn ::pc/sym}))

(fp/defsc MutationView
  [this {::pc/keys [sym params output]
         :ui/keys  [mutation-params-tree mutation-output-tree]}]
  {:pre-merge      (fn [{:keys [current-normalized data-tree]}]
                     (merge
                       {:ui/mutation-params-tree {}
                        :ui/mutation-output-tree {}}
                       current-normalized
                       data-tree))
   :ident          [::mutation-sym ::mutation-sym]
   :query          [::mutation-sym ::pc/sym ::pc/params ::pc/output
                    {:ui/mutation-params-tree (fp/get-query ex-tree/ExpandableTree)}
                    {:ui/mutation-output-tree (fp/get-query ex-tree/ExpandableTree)}]
   :css            [[:.title {:color color-mutation}
                     ui/css-header
                     ui/text-base]]
   :initLocalState (fn [] {:render (fn [{:keys [key]}]
                                     (attribute-link {::pc/attribute key} (-> this fp/props fp/get-computed)))})}
  (ui/column (ui/gc :.flex)
    (dom/h1 :.title (str sym))
    (ui/row (ui/gc :.flex :.scrollbars :.nowrap)
      (dom/div (ui/gc :.flex)
        (if params
          (ui/panel {::ui/panel-title "Params"}
            (ex-tree/expandable-tree mutation-params-tree
              {::ex-tree/root    (eql/query->ast params)
               ::ex-tree/render  (fp/get-state this :render)
               ::ex-tree/sort-by :key})))

        (render-plugin-extension this ::plugin-render-to-mutation-view-left))

      (dom/div {:style {:width "24px"}})

      (dom/div (ui/gc :.flex)
        (if output
          (ui/panel {::ui/panel-title "Output"}
            (ex-tree/expandable-tree mutation-output-tree
              {::ex-tree/root    (eql/query->ast output)
               ::ex-tree/render  (fp/get-state this :render)
               ::ex-tree/sort-by :key})))

        (render-plugin-extension this ::plugin-render-to-mutation-view-right)))))

(gobj/set MutationView "contextType" ExtensionContext)

(def mutation-view (fp/factory MutationView {:keyfn ::pc/sym}))

(defn realize-references [state coll]
  (mapv #(get-in state %) coll))

(declare SearchEverything)

(def max-search-results 100)

(s/def ::search-type
  #{::search-type-attribute
    ::search-type-resolver
    ::search-type-mutation})

(s/def ::search-value any?)

(s/def ::search-index-item
  (s/keys
    :req [::fuzzy/string ::search-value ::search-type]
    :opt [::fuzzy/match-hl]))

(defn active-search? [text]
  (> (count text) 2))

(fm/defmutation search [{::keys [text]}]
  (action [{:keys [ref state]}]
    (let [items     (get-in @state (conj ref ::search-vector))
          fuzzy-res (if (active-search? text)
                      (fuzzy/fuzzy-match {::fuzzy/options      items
                                          ::fuzzy/search-input text})
                      [])]
      (swap! state fp/merge-component SearchEverything (into {::search-results (vec (take max-search-results fuzzy-res))} [ref]))
      (swap! state update-in ref assoc ::text text))))

(defn remove-not-found [x]
  (p/transduce-maps
    (remove (fn [[_ v]] (contains? #{::p/not-found ::fp/not-found} v)))
    x))

(fp/defsc AllAttributesList
  [this {::keys [attributes] :as props} computed]
  {}
  (ui/collapsible-box (assoc props ::ui/title "Attributes")
    (dom/div
      (attribute-link {::pc/attribute #{}} computed)
      (into []
            (comp
              (filter (comp keyword? ::pc/attribute))
              (map (fn [{::pc/keys [attribute]}]
                     (attribute-link {::pc/attribute attribute
                                      :react-key     (pr-str attribute)} computed))))
            attributes))))

(def all-attributes-list (fp/computed-factory AllAttributesList))

(def last-value (atom nil))

(fp/defsc AllResolversList
  [this {::keys [resolvers] :as props} computed]
  {}
  (ui/collapsible-box (assoc props ::ui/title "Resolvers")
    (dom/div
      (mapv #(resolver-link % computed) resolvers))))

(def all-resolvers-list (fp/computed-factory AllResolversList))

(fp/defsc AllMutationsList
  [this {::keys [mutations] :as props} computed]
  {}
  (ui/collapsible-box (assoc props ::ui/title "Mutations")
    (dom/div
      (mapv #(mutation-link % computed) mutations))))

(def all-mutations-list (fp/computed-factory AllMutationsList))

(fp/defsc SearchEverything
  [this
   {::keys   [text search-results attributes resolvers mutations]
    :ui/keys [collapse-attributes? collapse-resolvers? collapse-mutations?]}
   computed]
  {:pre-merge      (fn [{:keys [current-normalized data-tree]}]
                     (merge
                       {::id                     (random-uuid)
                        ::text                   ""
                        ::search-results         []
                        :ui/collapse-attributes? false
                        :ui/collapse-resolvers?  false
                        :ui/collapse-mutations?  false}
                       current-normalized
                       data-tree))
   :ident          [::id ::id]
   :query          [::id ::text
                    {::search-results
                     [::fuzzy/string ::fuzzy/match-hl ::search-type ::search-value]}
                    {::attributes [::pc/attribute]}
                    {::resolvers [::pc/sym]}
                    {::mutations [::pc/sym]}

                    :ui/collapse-attributes?
                    :ui/collapse-resolvers?
                    :ui/collapse-mutations?]
   :css            [[:.container {:flex        "1"
                                  :white-space "nowrap"
                                  :overflow    "auto"}]]
   :initLocalState (fn [] {:search
                           #(fp/transact! this [`(search {::text ~(h/target-value %)})])

                           :toggle-attribute-collapse
                           #(fm/toggle! this :ui/collapse-attributes?)

                           :toggle-resolver-collapse
                           #(fm/toggle! this :ui/collapse-resolvers?)

                           :toggle-mutation-collapse
                           #(fm/toggle! this :ui/collapse-mutations?)

                           :all-attributes
                           (let [props    (fp/props this)
                                 computed (fp/get-computed props)]
                             (dom/div
                               (attribute-link {::pc/attribute #{}} computed)
                               (into []
                                     (comp
                                       (filter (comp keyword? ::pc/attribute))
                                       (map (fn [{::pc/keys [attribute]}]
                                              (attribute-link {::pc/attribute attribute} computed))))
                                     (::attributes props))))})}
  (ui/column (ui/gc :.flex)
    (ui/text-field {:placeholder   "Filter"
                    :value         text
                    :onChange      (fp/get-state this :search)
                    ::ui/left-icon :$fa-search
                    ::ui/on-clear  #(fm/set-value! this ::text "")})
    (ui/column (ui/gc :.flex :.scrollbars)
      (dom/div :.container {:style {:display (if-not (active-search? text) "none")}}
        (if (active-search? text)
          (into []
                (comp
                  (map (fn [{::keys       [search-value search-type]
                             ::fuzzy/keys [match-hl]}]
                         (case search-type
                           ::search-type-attribute
                           (attribute-link {::pc/attribute search-value
                                            ::ui/render    #(dom/div {:dangerouslySetInnerHTML {:__html match-hl}})} computed)

                           ::search-type-resolver
                           (resolver-link {::pc/sym    search-value
                                           ::ui/render #(dom/div {:dangerouslySetInnerHTML {:__html match-hl}})} computed)

                           ::search-type-mutation
                           (mutation-link {::pc/sym    search-value
                                           ::ui/render #(dom/div {:dangerouslySetInnerHTML {:__html match-hl}})} computed)))))
                search-results)))

      (dom/div :.container {:style {:display (if (> (count text) 2) "none")}}
        (all-attributes-list {::attributes    attributes
                              ::ui/collapsed? collapse-attributes?
                              ::ui/on-toggle  (fp/get-state this :toggle-attribute-collapse)} computed)
        (all-resolvers-list {::resolvers     resolvers
                             ::ui/collapsed? collapse-resolvers?
                             ::ui/on-toggle  (fp/get-state this :toggle-resolver-collapse)} computed)
        (all-mutations-list {::mutations     mutations
                             ::ui/collapsed? collapse-mutations?
                             ::ui/on-toggle  (fp/get-state this :toggle-mutation-collapse)} computed)))))

(def search-everything (fp/computed-factory SearchEverything))

(fp/defsc AttributeMismatchPanel
  [this {::keys [attr-type-mismatch attr-mismatch-expanded]} computed]
  {:pre-merge (fn [{:keys [current-normalized data-tree]}]
                (merge {::attr-mismatch-expanded #{}} current-normalized data-tree))
   :ident     [::id ::id]
   :query     [::id ::attr-mismatch-expanded
               {::attr-type-mismatch [::pc/attribute ::pc/attr-leaf-in ::pc/attr-branch-in]}]
   :css       [[:.resolver-container {:margin-left "26px"}]]}
  (ui/panel {::ui/panel-title "Attributes with type mismatch"}
    (for [{::pc/keys [attribute attr-leaf-in attr-branch-in]} attr-type-mismatch]
      (ui/raw-collapsible {:react-key      (pr-str attribute)
                           ::ui/collapsed? (not (contains? attr-mismatch-expanded attribute))
                           ::ui/on-toggle  #(h/update-value! this ::attr-mismatch-expanded h/toggle-set-item attribute)
                           ::ui/title      (attribute-link {::pc/attribute attribute} computed)}
        (dom/div :.resolver-container
          (for [resolver attr-branch-in]
            (resolver-link {::pc/sym resolver} computed))
          (dom/hr)
          (for [resolver attr-leaf-in]
            (resolver-link {::pc/sym resolver} computed)))))))

(def attribute-mismatch-panel (fp/computed-factory AttributeMismatchPanel))

(fp/defsc StatsView
  [this {::keys [attribute-count resolver-count mutation-count globals-count idents-count
                 attr-edges-count top-connection-hubs attr-type-mismatch]
         :>/keys [attr-type-mismatch-join]}
   computed]
  {:pre-merge (fn [{:keys [current-normalized data-tree]}]
                (let [id (or (::id data-tree)
                             (::id current-normalized)
                             (random-uuid))]
                  (merge {::id                       id
                          :>/attr-type-mismatch-join {::id id}} current-normalized data-tree)))
   :ident     [::id ::id]
   :query     [::id ::attribute-count ::resolver-count ::globals-count ::idents-count
               ::attr-edges-count ::mutation-count ::attr-type-mismatch
               {::top-connection-hubs [::pc/attribute ::attr-edges-count]}
               {:>/attr-type-mismatch-join (fp/get-query AttributeMismatchPanel)}]
   :css       [[:.container {:padding-right "12px"}]
               [:.title ui/text-base]]}
  (dom/div :.container (ui/gc :.flex :.scrollbars)
    (dom/h1 :.title "Stats")
    (ui/row {}
      (dom/div (ui/gc :.flex)
        (ui/panel {::ui/panel-title "Counters"}
          (dom/div "Attribute count: " attribute-count)
          (dom/div "Resolver count: " resolver-count)
          (dom/div "Mutation count: " mutation-count)
          (dom/div "Globals count: " globals-count)
          (dom/div "Idents count: " idents-count)
          (dom/div "Edges count: " attr-edges-count))
        (ui/panel {::ui/panel-title "Most Connected Attributes"}
          (for [{::pc/keys [attribute]
                 ::keys    [attr-edges-count]} top-connection-hubs]
            (attribute-link {::pc/attribute attribute
                             ::ui/render    #(str "[" attr-edges-count "] " (pr-str attribute))} computed))))
      (if (seq attr-type-mismatch)
        (fp/fragment
          (dom/div {:style {:width "24px"}})
          (dom/div (ui/gc :.flex)
            (attribute-mismatch-panel attr-type-mismatch-join computed)))))))

(def stats-view (fp/factory StatsView))

(fp/defsc FullGraphView
  [this _
   {::keys [attributes on-select-attribute on-select-resolver]}]
  {:ident [::graph-view-id ::graph-view-id]
   :query [::graph-view-id]
   :css   [[:.container {:padding-right "12px"
                         :flex          "1"}]
           [:.title ui/text-base]]}
  (ui/column {:classes (ui/ccss this :.container)}
    (dom/h1 :.title "Full Graph")
    (attribute-graph {::attributes       attributes
                      ::direct-reaches?  true
                      ::nested-reaches?  false
                      ::direct-provides? true
                      ::nested-provides? false
                      ::on-show-details  on-select-attribute
                      ::on-click-edge    #(on-select-resolver (first (::resolvers %)))})))

(def full-graph-view (fp/factory FullGraphView))

(defn prop-presence-ident [props]
  (fn [data]
    (or (some #(if-some [val (get data %)]
                 [% val]) props)
        [:invalid "ident"])))

(def main-view-ident (prop-presence-ident [::pc/attribute ::mutation-sym ::pc/sym ::id ::graph-view-id]))

(fp/defsc MainViewUnion
  [this props]
  {:ident (fn [] (main-view-ident props))
   :query (fn []
            {::pc/attribute  (fp/get-query AttributeView)
             ::pc/sym        (fp/get-query ResolverView)
             ::mutation-sym  (fp/get-query MutationView)
             ::id            (fp/get-query StatsView)
             ::graph-view-id (fp/get-query FullGraphView)})}
  (case (first (fp/get-ident this))
    ::pc/attribute (attribute-view props)
    ::pc/sym (resolver-view props)
    ::mutation-sym (mutation-view props)
    ::id (stats-view props)
    ::graph-view-id (full-graph-view props)
    (dom/div "Blank page")))

(def main-view-union (fp/computed-factory MainViewUnion {:keyfn #(or (::pc/attribute %) (::pc/sym %))}))

(defn augment [data f]
  (merge data (f data)))

(defn compute-stats [{::keys [attributes resolvers mutations globals idents] :as data}]
  {::attribute-count     (count attributes)
   ::resolver-count      (count resolvers)
   ::mutation-count      (count mutations)
   ::globals-count       (count globals)
   ::idents-count        (count idents)
   ::attr-edges-count    (transduce (map ::attr-edges-count) + attributes)
   ::attr-type-mismatch  (->> attributes
                              (filterv #(and (contains? % ::pc/attr-leaf-in)
                                             (contains? % ::pc/attr-branch-in))))
   ::top-connection-hubs (->> attributes
                              (sort-by ::attr-edges-count #(compare %2 %))
                              (take 30)
                              vec)})

(defn build-search-vector [{::pc/keys [index-resolvers index-attributes index-mutations]}]
  (->> (concat
         (->> index-resolvers
              vals
              (map #(hash-map ::fuzzy/string (pr-str (::pc/sym %))
                              ::search-value (::pc/sym %)
                              ::search-type ::search-type-resolver))
              (sort-by ::fuzzy/string))
         (->> index-mutations
              vals
              (map #(hash-map ::fuzzy/string (pr-str (::pc/sym %))
                              ::search-value (::pc/sym %)
                              ::search-type ::search-type-mutation))
              (sort-by ::fuzzy/string))
         (->> index-attributes
              vals
              (map #(hash-map ::fuzzy/string (pr-str (::pc/attribute %))
                              ::search-value (::pc/attribute %)
                              ::search-type ::search-type-attribute))
              (sort-by ::fuzzy/string)))
       vec))

(defn process-index [{::pc/keys [index-resolvers idents index-attributes index-mutations] :as index}]
  (let [attrs (->> index-attributes
                   (map (fn [[attr {::pc/keys [attr-reach-via attr-provides] :as data}]]
                          (assoc data
                            ::weight (count attr-provides)
                            ::reach (count attr-reach-via)
                            ::pc/attribute attr
                            ::attr-edges-count (+ (transduce (map count) + (vals attr-reach-via))
                                                 (transduce (map count) + (vals attr-provides)))
                            ::global-attribute? (contains? attr-reach-via #{})
                            ::ident-attribute? (contains? idents attr))))
                   (sort-by (comp pr-str ::pc/attribute))
                   (vec))]
    (-> {::attributes    attrs
         ::globals       (filterv ::global-attribute? attrs)
         ::idents        (filterv ::ident-attribute? attrs)

         ::search-vector (build-search-vector index)

         ::resolvers     (->> index-resolvers
                              vals
                              (map #(assoc % ::resolver? true))
                              (sort-by ::pc/sym)
                              vec)

         ::mutations     (->> index-mutations
                              vals
                              (map #(assoc % ::mutation? true ::mutation-sym (::pc/sym %)))
                              (sort-by ::pc/sym)
                              vec)}
        (augment compute-stats))))

;; Query

(fp/defsc AttributeIndex [_ _]
  {:ident [::pc/attribute ::pc/attribute]
   :query [::pc/attribute ::pc/attribute-paths ::pc/attr-provides ::pc/attr-reach-via
           ::pc/attr-combinations ::weight ::reach]})

(fp/defsc ResolverIndex [_ _]
  {:ident [::pc/sym ::pc/sym]
   :query [::pc/sym ::pc/input ::pc/output ::pc/params]})

(fp/defsc MutationIndex [_ _]
  {:ident [::mutation-sym ::mutation-sym]
   :query [::pc/sym ::mutation-sym ::pc/output ::pc/params]})

(def history-size-limit 200)

(defn history-append [{::keys [history history-index] :as x} ref]
  (let [index    (inc history-index)
        history' (conj
                   (subvec history (if (= index history-size-limit) 1 0) index)
                   ref)]
    (assoc x
      ::history history'
      ::history-index (dec (count history'))
      :ui/page ref)))

(fm/defmutation navigate-to-attribute [{::pc/keys [attribute]}]
  (action [{:keys [state ref]}]
    (swap! state fp/merge-component AttributeView {::pc/attribute attribute})
    (swap! state update-in ref history-append [::pc/attribute attribute])))

(fm/defmutation navigate-to-resolver [{::pc/keys [sym]}]
  (action [{:keys [state ref]}]
    (swap! state fp/merge-component ResolverView {::pc/sym sym})
    (swap! state update-in ref history-append [::pc/sym sym])))

(fm/defmutation navigate-to-mutation [{::keys [mutation-sym]}]
  (action [{:keys [state ref]}]
    (swap! state fp/merge-component MutationView {::mutation-sym mutation-sym})
    (swap! state update-in ref history-append [::mutation-sym mutation-sym])))

(fm/defmutation navigate-stats [_]
  (action [{:keys [state ref]}]
    (swap! state update-in ref history-append ref)))

(fm/defmutation navigate-graph-view [_]
  (action [{:keys [state ref]}]
    (let [id (second ref)]
      (swap! state fp/merge-component FullGraphView {::graph-view-id id})
      (swap! state update-in ref history-append [::graph-view-id id]))))

(defn can-go-back? [{::keys [history-index]}]
  (> history-index 0))

(defn can-go-forward? [{::keys [history history-index]}]
  (< history-index (dec (count history))))

(fm/defmutation navigate-backwards [_]
  (action [{:keys [state ref]}]
    (let [{::keys [history history-index] :as props} (get-in @state ref)]
      (if (can-go-back? props)
        (let [page (nth history (dec history-index))]
          (swap! state update-in ref assoc
            :ui/page page
            ::history-index (dec history-index)))))))

(fm/defmutation navigate-forwards [_]
  (action [{:keys [state ref]}]
    (let [{::keys [history history-index] :as props} (get-in @state ref)]
      (if (can-go-forward? props)
        (let [page (nth history (inc history-index))]
          (swap! state update-in ref assoc
            :ui/page page
            ::history-index (inc history-index)))))))

(defn clear-not-found [x]
  (p/elide-items (conj p/special-outputs ::fp/not-found) x))

(fp/defsc IndexExplorer
  [this {::keys   [index attributes]
         :ui/keys [menu page]
         :as      props}
   extensions]
  {:pre-merge      (fn [{:keys [current-normalized data-tree]}]
                     (let [v
                           (merge
                             (let [id (or (::id data-tree)
                                          (::id current-normalized)
                                          (random-uuid))]
                               {::id            id
                                ::history       [[::id id]]
                                ::history-index 0
                                :ui/menu        {::id id}
                                :ui/page        {::id id}})
                             current-normalized
                             (clear-not-found data-tree)
                             (if-let [index (get data-tree ::index)]
                               (process-index index)))]
                       v))
   :initial-state  {}
   :ident          [::id ::id]
   :query          [::id ::index ::history ::history-index
                    {:ui/menu (fp/get-query SearchEverything)}
                    {::attributes (fp/get-query AttributeIndex)}
                    {::globals (fp/get-query AttributeIndex)}
                    {::idents (fp/get-query AttributeIndex)}
                    {::top-connection-hubs (fp/get-query AttributeIndex)}
                    {::attr-type-mismatch (fp/get-query AttributeIndex)}
                    {::resolvers (fp/get-query ResolverIndex)}
                    {::mutations (fp/get-query MutationIndex)}
                    {:ui/page (fp/get-query MainViewUnion)}]
   :css            [[:.out-container {:width "100%"}]
                    [:.container {:flex     "1"
                                  :overflow "hidden"}]
                    [:.graph {:height  "800px"
                              :display "flex"
                              :border  "1px solid #000"}]
                    [:.menu {:margin-right  "16px"
                             :padding-right "16px"
                             :overflow      "auto"
                             :width         "30%"}]
                    [:$row-center {:display "flex" :align-items "center"}]
                    [:$scrollbars {:overflow "auto"}]
                    [:$tag-spaced
                     [:$tag {:margin-left "4px"}]]]
   :css-include    [AttributeLink ResolverLink MutationLink ui/UIKit]
   :initLocalState (fn [] {:select-attribute #(fp/transact! this [`(navigate-to-attribute {::pc/attribute ~%})])
                           :select-resolver  #(fp/transact! this [`(navigate-to-resolver {::pc/sym ~%})])
                           :select-mutation  #(fp/transact! this [`(navigate-to-mutation {::mutation-sym ~%})])
                           :go-back          #(fp/transact! this [`(navigate-backwards)])
                           :go-forward       #(fp/transact! this [`(navigate-forwards)])
                           :go-stats         #(fp/transact! this [`(navigate-stats)])
                           :go-graph-view    #(fp/transact! this [`(navigate-graph-view)])})}
  (dom/create-element (gobj/get ExtensionContext "Provider") #js {:value extensions}
    (ui/row {:react-key "container" :classes (ui/ccss this :.out-container)}
      (ui/column {:classes (ui/ccss this :.menu)}
        (search-everything menu {::on-select-attribute (fp/get-state this :select-attribute)
                                 ::on-select-resolver  (fp/get-state this :select-resolver)
                                 ::on-select-mutation  (fp/get-state this :select-mutation)}))
      (ui/column (ui/gc :.flex :.no-scrollbars)
        (ui/row {}
          (ui/button {:onClick  (fp/get-state this :go-back)
                      :disabled (not (can-go-back? props))}
            "◀")
          (ui/button {:onClick  (fp/get-state this :go-forward)
                      :disabled (not (can-go-forward? props))}
            "▶")
          (ui/button {:onClick  (fp/get-state this :go-stats)
                      :disabled (= (main-view-ident page) (fp/get-ident this))
                      :style    {:marginLeft "12px"}} "Stats")
          (ui/button {:onClick  (fp/get-state this :go-graph-view)
                      :disabled (= (first (main-view-ident page)) ::graph-view-id)
                      :style    {:marginLeft "12px"}} "Full Graph"))
        (if page
          (main-view-union page (assoc index
                                  ::attributes attributes
                                  ::on-select-attribute (fp/get-state this :select-attribute)
                                  ::on-select-resolver (fp/get-state this :select-resolver)
                                  ::on-select-mutation (fp/get-state this :select-mutation))))

        #_(dom/div :.graph
            (attribute-graph {::attributes       attributes
                              ::direct-reaches?  true
                              ::nested-reaches?  true
                              ::direct-provides? true
                              ::nested-provides? true}))))))

(def index-explorer (fp/computed-factory IndexExplorer))
