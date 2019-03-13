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

; region shared css

(def css-attribute-font
  {:color       "#9a45b1"
   ; color "#660e7a"
   :font-size   "14px"
   :line-height "1.4em"})

(def css-resolver-font
  {:color       "#467cb7"
   :font-size   "14px"
   :line-height "1.4em"})

; endregion

(def ExtensionContext (js/React.createContext {}))

;; Views

(defn attribute-graph-events [this k]
  (let [on-select-attribute (-> this fp/props fp/get-computed ::on-select-attribute)]
    {:onClick      #(on-select-attribute k)
     :onMouseEnter #(if-let [settings (some-> (fp/get-state this :graph-comm) deref)]
                      ((gobj/get settings "highlightNode") (str k)))
     :onMouseLeave #(if-let [settings (some-> (fp/get-state this :graph-comm) deref)]
                      ((gobj/get settings "unhighlightNode") (str k)))}))

(defn resolver-graph-events [this k]
  (let [on-select-resolver (-> this fp/props fp/get-computed ::on-select-resolver)]
    {:onClick      #(on-select-resolver k)
     :onMouseEnter #(if-let [settings @(fp/get-state this :graph-comm)]
                      ((gobj/get settings "highlightEdge") (str k)))
     :onMouseLeave #(if-let [settings @(fp/get-state this :graph-comm)]
                      ((gobj/get settings "unhighlightEdge") (str k)))}))

(fp/defsc AttributeText
  [this {::pc/keys [attribute] ::ui/keys [render] :as props}]
  {:css [[:.container css-attribute-font]
         [:.pointer {:cursor "pointer"}]]}
  (dom/div :.container (ui/props (merge (attribute-graph-events this attribute) props))
    (if render (render props) (pr-str attribute))))

(def attribute-text (fp/computed-factory AttributeText))

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
  (let [index (h/index-by ::pc/attribute attributes)]
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

(defn attribute-link
  [{::pc/keys [index-oir]
    ::keys    [on-select-attribute]}
   attr]
  (if (contains? index-oir attr)
    (dom/a {:href "#" :onClick (h/pd #(on-select-attribute attr))}
      (pr-str attr))
    (pr-str attr)))

(defn pull-attr [{::keys [attr-index interconnections?]} attr]
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

(fp/defsc SimpleAttribute
  [this props]
  {:pre-merge (fn [{:keys [current-normalized data-tree]}]
                (merge {} current-normalized data-tree))
   :css       [[:.container {:cursor  "pointer"
                             :padding "0 2px"}
                css-attribute-font]]}
  (apply dom/div :.container props (fp/children this)))

(def simple-attribute (fp/factory SimpleAttribute))

(>defn attr-provides->tree [attr-provides]
  [::pc/attr-provides => ::ex-tree/root]
  (let [index    (->> attr-provides
                      (map #(update % 0 (fn [x] (if (keyword? x) [x] x))))
                      (map (fn [[path resolvers]]
                             (let [k (peek path)]
                               [path {:key k ::pc/sym-set resolvers}])))
                      (into {}))

        provides (reduce
                   (fn [{:keys [items index]} [path node]]
                     (if (> (count path) 1)
                       (let [prev (subvec path 0 (dec (count path)))]
                         {:items items
                          :index (update-in index [prev :children] (fnil conj [])
                                   node)})
                       {:items (conj items (get index path))
                        :index index}))
                   {:items []
                    :index index}
                   (->> index
                        (sort-by first h/vector-compare)
                        (reverse)))]
    {:children (:items provides)}))

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
          (attribute-text {::pc/attribute input
                           :classes       [:.pointer]
                           :style         (cond-> {} direct? (assoc :fontWeight "bold"))}
            computed)
          (if nested-reaches?
            (for [[path resolvers] (->> v
                                        (map #(update % 0 (fn [x] (if (set? x) [x] x))))
                                        (sort-by (comp #(update % 0 (comp vec sort)) first)))
                  :let [path' (next path)]
                  :when path']
              (dom/div {:key   (pr-str path)
                        :style {:marginLeft (str 10 "px")}}
                (for [[k i] (map vector path' (range))]
                  (attribute-text {::pc/attribute k
                                   :classes       [:.pointer]
                                   :style         {:marginLeft (str (* i 10) "px")}}
                    computed))))))))))

(def attribute-info-reach-via (fp/computed-factory AttributeInfoReachVia))

(fp/defsc AttributeView
  [this {::pc/keys [attr-combinations attribute attr-reach-via attr-provides attr-input-in attr-output-in]
         ::keys    [attr-depth direct-reaches? nested-reaches? direct-provides?
                    nested-provides? interconnections? show-graph?]
         :>/keys   [reach-via]
         :ui/keys  [provides-tree provides-tree-source]}
   {::keys [on-select-attribute attributes]
    :as    computed}]
  {:pre-merge      (fn [{:keys [current-normalized data-tree]}]
                     (let [attr          (or (::pc/attribute data-tree)
                                             (::pc/attribute current-normalized))
                           attr-provides (or (::pc/attr-provides data-tree)
                                             (::pc/attr-provides current-normalized))]
                       (merge
                         {::attr-depth        1
                          ::direct-reaches?   true
                          ::nested-reaches?   false
                          ::direct-provides?  true
                          ::nested-provides?  false
                          ::interconnections? true
                          ::show-graph?       false
                          :>/reach-via        {::pc/attribute attr}
                          :ui/provides-tree   {}}
                         current-normalized
                         data-tree
                         (if attr-provides
                           {:ui/provides-tree-source (attr-provides->tree attr-provides)}))))
   :ident          [::pc/attribute ::pc/attribute]
   :query          [::pc/attribute ::attr-depth ::direct-reaches? ::nested-reaches?
                    ::pc/attr-combinations ::pc/attr-input-in ::pc/attr-output-in
                    ::direct-provides? ::nested-provides? ::interconnections? ::show-graph? ::pc/attr-reach-via ::pc/attr-provides
                    {:>/reach-via (fp/get-query AttributeInfoReachVia)}
                    {:ui/provides-tree (fp/get-query ex-tree/ExpandableTree)}
                    :ui/provides-tree-source]
   :css            [[:.container {:flex           "1"
                                  :flex-direction "column"
                                  :display        "flex"}]
                    [:.toolbar {:display               "grid"
                                :grid-template-columns "repeat(10, max-content)"
                                :grid-gap              "10px"
                                :align-items           "center"
                                :margin-bottom         "16px"}]
                    [:.data-list {:white-space   "nowrap"
                                  ;:overflow      "auto"
                                  :box-sizing    "border-box"
                                  :width         "50%"
                                  :padding-right "12px"}]
                    [:.data-list-right {:white-space "nowrap"
                                        :width       "50%"
                                        :box-sizing  "border-box"
                                        ;:overflow    "auto"
                                        ;     :width       "300px"
                                        :padding     "0 12px"}]
                    [:.data-header {:padding     "9px 4px"
                                    :font-weight "bold"
                                    :font-family "Verdana"}]
                    [:.out-attr {:padding "0 2px"
                                 :cursor  "pointer"}
                     css-attribute-font]
                    [:.resolver {:cursor "pointer"}
                     css-resolver-font]
                    [:.path {:margin-bottom "6px"}]
                    [:.provides-container {:margin-left "8px"}]
                    [:.graph {:height         "400px"
                              :width          "100%"
                              :display        "flex"
                              :align-items    "stretch"
                              :flex-direction "column"}]
                    [:.columns {:display "flex"
                                :flex    "1"}
                     [:text {:font "bold 16px Verdana, Helvetica, Arial, sans-serif"}]]]
   :css-include    [AttributeGraph]
   :initLocalState (fn [] {:graph-comm      (atom nil)
                           :select-resolver (fn [{::keys [resolvers]}]
                                              (let [{::keys [on-select-resolver]} (fp/get-computed (fp/props this))]
                                                (on-select-resolver (first resolvers))))})}
  (dom/div :.container
    (dom/div :.toolbar
      (dom/h1 :$title$is-marginless (pr-str attribute))

      (dom/div :$row-center
        (dom/label :$label$is-small "Depth")
        (dom/input :$input$is-small {:type     "number" :min 1 :value attr-depth
                                     :onChange #(fm/set-integer! this ::attr-depth :event %)}))
      (dom/label
        (dom/input {:type     "checkbox" :checked show-graph?
                    :onChange #(fm/set-value! this ::show-graph? (gobj/getValueByKeys % "target" "checked"))})
        "Graph")
      (dom/label
        (dom/input {:type     "checkbox" :checked direct-reaches?
                    :onChange #(fm/set-value! this ::direct-reaches? (gobj/getValueByKeys % "target" "checked"))})
        "Direct inputs")
      (dom/label
        (dom/input {:type     "checkbox" :checked nested-reaches?
                    :onChange #(fm/set-value! this ::nested-reaches? (gobj/getValueByKeys % "target" "checked"))})
        "Nested inputs")
      (dom/label
        (dom/input {:type     "checkbox" :checked direct-provides?
                    :onChange #(fm/set-value! this ::direct-provides? (gobj/getValueByKeys % "target" "checked"))})
        "Direct outputs")
      (dom/label
        (dom/input {:type     "checkbox" :checked nested-provides?
                    :onChange #(fm/set-value! this ::nested-provides? (gobj/getValueByKeys % "target" "checked"))})
        "Nested outputs")
      (dom/label
        (dom/input {:type     "checkbox" :checked interconnections?
                    :onChange #(fm/set-value! this ::interconnections? (gobj/getValueByKeys % "target" "checked"))})
        "Interconnections"))

    (if show-graph?
      (ui/panel {::ui/panel-title "Graph"
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
              (dom/div :.resolver (assoc (resolver-graph-events this resolver) :key (pr-str resolver))
                (pr-str resolver)))))

        (if (seq attr-combinations)
          (ui/panel {::ui/panel-title "Input Combinations"
                     ::ui/panel-tag   (count attr-combinations)}
            (for [input (sort-by (comp vec sort) h/vector-compare (map #(into (sorted-set) %) attr-combinations))]
              (dom/div :.out-attr (assoc (attribute-graph-events this input) :key (pr-str input))
                (pr-str input)))))

        (if-let [form (si/safe-form attribute)]
          (fp/fragment
            (ui/panel {::ui/panel-title "Spec"}
              (pr-str form))

            (ui/panel {::ui/panel-title "Examples"}
              (try
                (for [example (gen/sample (s/gen attribute))]
                  (dom/div {:key (pr-str example)} (pr-str example)))
                (catch :default _
                  (dom/div "Error generating samples"))))))

        (render-plugin-extension this ::plugin-render-to-attr-left-menu))

      (dom/div :.data-list-right
        (if (seq attr-provides)
          (ui/panel {::ui/panel-title "Provides"
                     ::ui/panel-tag   (count attr-provides)}
            (ex-tree/expandable-tree provides-tree
              {::ex-tree/root    provides-tree-source
               ::ex-tree/render  (fn [{:keys [key]}]
                                   (dom/div (assoc (attribute-graph-events this key)
                                              :classes [(-> (css/get-classnames AttributeView) :out-attr)])
                                     (pr-str key)))
               ::ex-tree/sort-by :key})))

        (if (seq attr-input-in)
          (ui/panel {::ui/panel-title "Input In"
                     ::ui/panel-tag   (count attr-input-in)}
            (for [resolver (sort attr-input-in)]
              (dom/div :.resolver (assoc (resolver-graph-events this resolver) :key (pr-str resolver))
                (pr-str resolver)))))))))

(gobj/set AttributeView "contextType" ExtensionContext)

(def attribute-view (fp/computed-factory AttributeView {:keyfn ::pc/attribute}))

(fp/defsc OutputAttributeView
  [this {:keys [key children] :as props}]
  {:css [[:.container {:background  "#263238"
                       :color       "#fff"
                       :display     "flex"
                       :font-family "'Open Sans'"
                       :padding     "10px"}]
         [:.title {:flex "1"}]]}
  (dom/div :.container {:key (pr-str key)}
    (dom/div :.title (attribute-link props key))))

(def output-attribute-view (fp/computed-factory OutputAttributeView {:keyfn (comp pr-str :key)}))

(>defn out-all-attributes [{:keys [children]}]
  [:edn-query-language.ast/node => (s/coll-of ::p/attribute :kind set?)]
  (reduce
    (fn [attrs {:keys [key children] :as node}]
      (cond-> (conj attrs key)
        children
        (into (out-all-attributes node))))
    #{}
    children))

(fp/defsc ResolverView
  [this {::pc/keys [sym input output]
         :ui/keys  [output-tree]}
   {::keys [on-select-attribute attributes]}
   css]
  {:pre-merge      (fn [{:keys [current-normalized data-tree]}]
                     (merge
                       {:ui/output-tree {}}
                       current-normalized
                       data-tree))
   :ident          [::pc/sym ::pc/sym]
   :query          [::pc/sym ::pc/input ::pc/output
                    {:ui/output-tree (fp/get-query ex-tree/ExpandableTree)}]
   :css            [[:.container {:flex           "1"
                                  :display        "flex"
                                  :flex-direction "column"}]
                    [:.data-header {:padding     "9px 4px"
                                    :font-weight "bold"
                                    :font-family "Verdana"}]
                    [:.header {:background  "#40879e"
                               :display     "flex"
                               :cursor      "pointer"
                               :color       "#fff"
                               :align-items "center"
                               :font-family "'Open Sans'"
                               :padding     "6px 8px"
                               :font-size   "14px"
                               :margin      "1px 0"}
                     [:b {:background "#F57F17"}]]
                    [:.attribute {:cursor "pointer"} css-attribute-font]
                    [:.columns {:display "flex"
                                :flex    1}]
                    [:.menu {:white-space   "nowrap"
                             :padding-right "12px"
                             :overflow      "auto"}]]
   :css-include    [OutputAttributeView]
   :initLocalState (fn [] {:graph-comm      (atom nil)
                           :select-resolver (fn [{::keys [resolvers]}]
                                              (let [{::keys [on-select-resolver]} (fp/get-computed (fp/props this))]
                                                (on-select-resolver (first resolvers))))
                           :render          (fn [{:keys [key]}]
                                              (dom/div (assoc (attribute-graph-events this key)
                                                         :classes [(:attribute (css/get-classnames ResolverView))])
                                                (pr-str key)))})}
  (let [input'         (if (= 1 (count input)) (first input) input)
        resolver-attrs (conj (out-all-attributes (->> output eql/query->ast)) input')
        attrs          (-> (h/index-by ::pc/attribute attributes)
                           (select-keys resolver-attrs)
                           (update input' assoc ::center? true)
                           vals)]
    (dom/div :.container
      (dom/h1 :$title (str sym))
      (dom/div :.columns
        (dom/div :.menu
          (ui/panel {::ui/panel-title "Input"}
            (dom/div :.attribute (attribute-graph-events this (if (= 1 (count input))
                                                                (first input)
                                                                input)) (h/pprint-str input)))
          (if output
            (ui/panel {::ui/panel-title "Output"}
              (ex-tree/expandable-tree output-tree
                {::ex-tree/root    (eql/query->ast output)
                 ::ex-tree/render  (fp/get-state this :render)
                 ::ex-tree/sort-by :key})))

          (render-plugin-extension this ::plugin-render-to-resolver-menu))

        (attribute-graph {::attributes      attrs
                          ::graph-comm      (fp/get-state this :graph-comm)
                          ::on-show-details on-select-attribute
                          ::on-click-edge   (fp/get-state this :select-resolver)})))))

(gobj/set ResolverView "contextType" ExtensionContext)

(def resolver-view (fp/factory ResolverView {:keyfn ::pc/sym}))

(defn realize-references [state coll]
  (mapv #(get-in state %) coll))

(declare SearchEverything)

(def max-search-results 100)

(fm/defmutation search [{::keys [text]}]
  (action [{:keys [ref state]}]
    (let [attributes (->> (get-in @state (conj ref ::attributes))
                          (realize-references @state)
                          (mapv #(assoc % ::fuzzy/string (pr-str (::pc/attribute %)))))
          fuzzy-res  (if (> (count text) 2)
                       (fuzzy/fuzzy-match {::fuzzy/options      attributes
                                           ::fuzzy/search-input text})
                       [])]
      (swap! state fp/merge-component SearchEverything (into {::search-results (vec (take max-search-results fuzzy-res))} [ref]))
      (swap! state update-in ref assoc ::text text))))

(defn remove-not-found [x]
  (p/transduce-maps
    (remove (fn [[_ v]] (contains? #{::p/not-found ::fp/not-found} v)))
    x))

(fp/defsc AllAttributesList
  [this {::keys [attributes]} computed]
  {}
  (dom/div
    (attribute-text {::pc/attribute #{} :classes [:.pointer]} computed)
    (into []
          (comp
            (filter (comp keyword? ::pc/attribute))
            (map (fn [{::pc/keys [attribute]}]
                   (attribute-text {::pc/attribute attribute
                                    :classes       [:.pointer]
                                    :react-key     (pr-str attribute)} computed))))
          attributes)))

(def all-attributes-list (fp/computed-factory AllAttributesList))

(def last-value (atom nil))

(fp/defsc SearchEverything
  [this {::keys [text search-results attributes]} computed]
  {:pre-merge      (fn [{:keys [current-normalized data-tree]}]
                     (merge
                       {::id             (random-uuid)
                        ::text           ""
                        ::search-results []}
                       current-normalized
                       data-tree))
   :ident          [::id ::id]
   :query          [::id ::text
                    {::search-results [::pc/attribute ::fuzzy/match-hl]}
                    {::attributes [::pc/attribute]}]
   :css            [[:.attributes {:white-space "nowrap"
                                   :width       "300px"
                                   :overflow    "auto"}]]
   :initLocalState (fn [] {:search
                           #(fp/transact! this [`(search {::text ~(h/target-value %)})])

                           :all-attributes
                           (let [props    (fp/props this)
                                 computed (fp/get-computed props)]
                             (dom/div
                               (attribute-text {::pc/attribute #{} :classes [:.pointer]} computed)
                               (into []
                                     (comp
                                       (filter (comp keyword? ::pc/attribute))
                                       (map (fn [{::pc/keys [attribute]}]
                                              (attribute-text {::pc/attribute attribute
                                                               :classes       [:.pointer]
                                                               :react-key     (pr-str attribute)} computed))))
                                     (::attributes props))))})}
  (ui/column {}
    (dom/div :$control$has-icons-left$has-icons-right
      (dom/input :$input$is-small
        {:type        "text"
         :value       text
         :placeholder "Filter"
         :onChange    (fp/get-state this :search)})
      (dom/span :$icon$is-small$is-left (dom/i :$fas$fa-search))
      (if (seq text)
        (dom/span :$icon$is-small$is-right {:onClick #(fm/set-value! this ::text "")}
          (dom/a :$delete$is-small))))
    (dom/div (ui/gc :.flex :.scrollbars)
      (dom/div :.attributes
        (if (seq text)
          (into []
                (comp
                  (filter (comp keyword? ::pc/attribute))
                  (map (fn [{::pc/keys    [attribute]
                             ::fuzzy/keys [match-hl]}]
                         (attribute-text {::pc/attribute attribute
                                          ::ui/render    #(dom/div {:dangerouslySetInnerHTML {:__html match-hl}})
                                          :classes       [:.pointer]
                                          :react-key     (pr-str attribute)} computed))))
                search-results)))

      (dom/div :.attributes {:style {:display (if (> (count text) 2) "none")}}
        (all-attributes-list {::attributes attributes} computed)))))

(def search-everything (fp/computed-factory SearchEverything))

(fp/defsc StatsView
  [this {::keys [attribute-count resolver-count globals-count idents-count
                 attr-edges-count top-connection-hubs]}
   {::keys [on-select-attribute]}]
  {:pre-merge (fn [{:keys [current-normalized data-tree]}]
                (merge {} current-normalized data-tree))
   :ident     [::id ::id]
   :query     [::id ::attribute-count ::resolver-count ::globals-count ::idents-count
               ::attr-edges-count
               {::top-connection-hubs [::pc/attribute ::attr-edges-count]}]}
  (fp/fragment
    (dom/h1 :$title "Stats")
    (dom/div :$content
      (dom/div "Attribute count: " attribute-count)
      (dom/div "Resolver count: " resolver-count)
      (dom/div "Globals count: " globals-count)
      (dom/div "Idents count: " idents-count)
      (dom/div "Edges count: " attr-edges-count))
    (dom/h2 :$subtitle "Most Connected Attributes")
    (dom/div :$content
      (for [{::pc/keys [attribute]
             ::keys    [attr-edges-count]} top-connection-hubs]
        (simple-attribute {:react-key (pr-str attribute)
                           :onClick   #(on-select-attribute attribute)}
          (str "[" attr-edges-count "] " (pr-str attribute)))))))

(def stats-view (fp/factory StatsView {:keyfn ::id}))

(defn prop-presence-ident [props]
  (fn [data]
    (or (some #(if-some [val (get data %)]
                 [% val]) props)
        [:invalid "ident"])))

(def main-view-ident (prop-presence-ident [::id ::pc/sym ::pc/attribute]))

(fp/defsc MainViewUnion
  [this props]
  {:ident (fn [] (main-view-ident props))
   :query (fn []
            {::pc/attribute (fp/get-query AttributeView)
             ::pc/sym       (fp/get-query ResolverView)
             ::id           (fp/get-query StatsView)})}
  (case (first (fp/get-ident this))
    ::pc/attribute (attribute-view props)
    ::pc/sym (resolver-view props)
    ::id (stats-view props)
    (dom/div "Blank page")))

(def main-view-union (fp/computed-factory MainViewUnion {:keyfn #(or (::pc/attribute %) (::pc/sym %))}))

(defn augment [data f]
  (merge data (f data)))

(defn compute-stats [{::keys [attributes resolvers globals idents] :as data}]
  {::attribute-count     (count attributes)
   ::resolver-count      (count resolvers)
   ::globals-count       (count globals)
   ::idents-count        (count idents)
   ::attr-edges-count    (transduce (map ::attr-edges-count) + attributes)
   ::top-connection-hubs (->> attributes
                              (sort-by ::attr-edges-count #(compare %2 %))
                              (take 10)
                              vec)})

(defn process-index [{::pc/keys [index-resolvers idents index-attributes]}]
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
    (-> {::attributes attrs
         ::globals    (filterv ::global-attribute? attrs)
         ::idents     (filterv ::ident-attribute? attrs)

         ::resolvers  (->> index-resolvers
                           vals
                           (sort-by ::pc/sym)
                           vec)
         ;:ui/page     {::pc/attribute :customer/cpf}
         }
        (augment compute-stats))))

;; Query

(fp/defsc AttributeIndex [_ _]
  {:ident [::pc/attribute ::pc/attribute]
   :query [::pc/attribute ::pc/attribute-paths ::pc/attr-provides ::pc/attr-reach-via
           ::pc/attr-combinations ::weight ::reach]})

(fp/defsc ResolverIndex [_ _]
  {:ident [::pc/sym ::pc/sym]
   :query [::pc/sym ::pc/input ::pc/output ::pc/params]})

(fm/defmutation navigate-to-resolver [{::pc/keys [sym]}]
  (action [{:keys [state ref]}]
    (swap! state fp/merge-component ResolverView {::pc/sym sym}
      :replace (conj ref :ui/page))))

(fm/defmutation navigate-to-attribute [{::pc/keys [attribute]}]
  (action [{:keys [state ref]}]
    (swap! state fp/merge-component AttributeView {::pc/attribute attribute}
      :replace (conj ref :ui/page))))

(fp/defsc IndexExplorer
  [this {::keys   [index attributes]
         :ui/keys [menu page]}
   extensions]
  {:pre-merge      (fn [{:keys [current-normalized data-tree]}]
                     (merge
                       (let [id (or (::id data-tree)
                                    (::id current-normalized)
                                    (random-uuid))]
                         {::id     id
                          :ui/menu {::id id}
                          :ui/page {::id id}
                          ;:ui/page {::pc/attribute :account/id}
                          })
                       current-normalized
                       data-tree
                       (if-let [index (get data-tree ::index)]
                         (process-index index))))
   :initial-state  {}
   :ident          [::id ::id]
   :query          [::id ::index
                    {:ui/menu (fp/get-query SearchEverything)}
                    {::attributes (fp/get-query AttributeIndex)}
                    {::globals (fp/get-query AttributeIndex)}
                    {::idents (fp/get-query AttributeIndex)}
                    {::top-connection-hubs (fp/get-query AttributeIndex)}
                    {::resolvers (fp/get-query ResolverIndex)}
                    {:ui/page (fp/get-query MainViewUnion)}]
   :css            [[:.out-container {:width "100%"}]
                    [:.container {:flex           "1"
                                  :overflow       "hidden"}]
                    [:.graph {:height  "800px"
                              :display "flex"
                              :border  "1px solid #000"}]
                    [:.menu {:margin-right  "16px"
                             :padding-right "16px"
                             :overflow      "auto"}]
                    [:$row-center {:display "flex" :align-items "center"}]
                    [:$scrollbars {:overflow "auto"}]
                    [:$tag-spaced
                     [:$tag {:margin-left "4px"}]]]
   :css-include    [SimpleAttribute AttributeText ui/UIKit]
   :initLocalState (fn [] {:select-attribute #(fp/transact! this [`(navigate-to-attribute {::pc/attribute ~%})])
                           :select-resolver  #(fp/transact! this [`(navigate-to-resolver {::pc/sym ~%})])})}
  (dom/create-element (gobj/get ExtensionContext "Provider") #js {:value extensions}
    (ui/row {:react-key "container" :classes (ui/ccss this :.out-container)}
      (dom/div :.menu
        (search-everything menu {::on-select-attribute (fp/get-state this :select-attribute)
                                 ::on-select-resolver  (fp/get-state this :select-resolver)}))
      (ui/column (ui/gc :.flex :.no-scrollbars)
        (if page
          (main-view-union page (assoc index
                                  ::attributes attributes
                                  ::on-select-attribute (fp/get-state this :select-attribute)
                                  ::on-select-resolver (fp/get-state this :select-resolver))))

        #_(dom/div :.graph
            (attribute-graph {::attributes       attributes
                              ::direct-reaches?  true
                              ::nested-reaches?  true
                              ::direct-provides? true
                              ::nested-provides? true}))))))

(def index-explorer (fp/computed-factory IndexExplorer))
