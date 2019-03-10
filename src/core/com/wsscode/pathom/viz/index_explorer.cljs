(ns com.wsscode.pathom.viz.index-explorer
  (:require ["./d3-attribute-graph" :as d3attr]
            ["./detect-element-size" :refer [addResizeListener]]
            [cljs.reader :refer [read-string]]
            [cljs.spec.alpha :as s]
            [com.wsscode.fuzzy :as fuzzy]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.viz.helpers :as h]
            [edn-query-language.core :as eql]
            [fulcro.client.localized-dom :as dom]
            [fulcro.client.mutations :as fm]
            [fulcro.client.primitives :as fp]
            [com.wsscode.pathom.viz.ui.expandable-tree :as ex-tree]
            [ghostwheel.core :as g :refer [>defn >defn- >fdef => | <- ?]]
            [goog.object :as gobj]
            [clojure.string :as str]
            [fulcro-css.css :as css]
            [com.wsscode.spec-inspec :as si]
            [clojure.test.check.generators :as gen]))

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
   :font-family "sans-serif"
   :font-size   "14px"
   :line-height "1.4em"})

; endregion

(def ExtensionContext (js/React.createContext {}))

;; Views

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



(fp/defsc AttributeLineView
  [this {::pc/keys    [attribute]
         ::fuzzy/keys [match-hl]
         ::keys       [global-attribute? ident-attribute?]}
   {::keys [on-select-attribute highlight?]
    :or    {on-select-attribute identity}}]
  {:pre-merge (fn [{:keys [current-normalized data-tree]}]
                (merge {} current-normalized data-tree))
   :ident     [::pc/attribute ::pc/attribute]
   :query     [::pc/attribute ::global-attribute? ::ident-attribute?
               ::fuzzy/match-hl]
   :css       [[:.attribute {:background  "#263238"
                             :display     "flex"
                             :cursor      "pointer"
                             :color       "#fff"
                             :align-items "center"
                             :font-family "'Open Sans'"
                             :padding     "6px 8px"
                             :font-size   "14px"
                             :margin      "1px 0"}
                [:b {:background "#F57F17"}]]
               [:.link {:flex "1"}]
               [:.global {:background  "#FFB74D"
                          :width       "20px"
                          :text-align  "center"
                          :font-weight "bold"
                          :margin-left "5px"}]
               [:.ident {:background  "#4DB6AC"
                         :width       "20px"
                         :text-align  "center"
                         :font-weight "bold"
                         :margin-left "5px"}]]}
  (dom/div :.attribute {:onClick (h/pd #(on-select-attribute attribute))}
    (dom/div :.link
      (cond-> {}
        (and highlight? match-hl)
        (assoc :dangerouslySetInnerHTML {:__html match-hl}))
      (if-not (and highlight? match-hl)
        (pr-str attribute)))
    (if global-attribute?
      (dom/div :.global "G"))
    (if ident-attribute?
      (dom/div :.ident "I"))))

(def attribute-line-view (fp/computed-factory AttributeLineView {:keyfn ::pc/attribute}))

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

(defn out-attribute-events [this k]
  (let [on-select-attribute (-> this fp/props fp/get-computed ::on-select-attribute)]
    {:onClick      #(on-select-attribute k)
     :onMouseEnter #(if-let [settings @(fp/get-state this :graph-comm)]
                      ((gobj/get settings "highlightNode") (str k)))
     :onMouseLeave #(if-let [settings @(fp/get-state this :graph-comm)]
                      ((gobj/get settings "unhighlightNode") (str k)))}))

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

(fp/defsc AttributeView
  [this {::pc/keys [attribute-paths attribute attr-reach-via attr-provides]
         ::keys    [attr-depth direct-reaches? nested-reaches? direct-provides?
                    nested-provides? interconnections? show-graph?]
         :ui/keys  [provides-tree provides-tree-source]
         :>/keys   [header-view]}
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
                          ::show-graph?       true
                          :>/header-view      {::pc/attribute attr}
                          :ui/provides-tree   {}}
                         current-normalized
                         data-tree
                         (if attr-provides
                           {:ui/provides-tree-source (attr-provides->tree attr-provides)}))))
   :ident          [::pc/attribute ::pc/attribute]
   :query          [::pc/attribute ::pc/attribute-paths ::attr-depth ::direct-reaches? ::nested-reaches?
                    ::direct-provides? ::nested-provides? ::interconnections? ::show-graph? ::pc/attr-reach-via ::pc/attr-provides
                    {::attr-provides [::pc/attribute]}
                    {:ui/provides-tree (fp/get-query ex-tree/ExpandableTree)}
                    :ui/provides-tree-source
                    {:>/header-view (fp/get-query AttributeLineView)}]
   :css            [[:.container {:flex           "1"
                                  :flex-direction "column"
                                  :display        "flex"}]
                    [:.toolbar {:display               "grid"
                                :grid-template-columns "repeat(10, max-content)"
                                :grid-gap              "10px"}]
                    [:.data-list {:white-space  "nowrap"
                                  :border-right "1px solid #000"
                                  :overflow     "auto"
                                  :width        "260px"}]
                    [:.data-list-right {:white-space "nowrap"
                                        :border-left "1px solid #000"
                                        :overflow    "auto"
                                        :width       "300px"}]
                    [:.data-header {:padding     "9px 4px"
                                    :font-weight "bold"
                                    :font-family "Verdana"}]
                    [:.out-attr {:padding "0 2px"
                                 :cursor  "pointer"}
                     css-attribute-font]
                    [:.path {:margin-bottom "6px"}]
                    [:.provides-container {:margin-left "8px"}]
                    [:.graph {:display "flex"
                              :flex    "1"
                              :border  "1px solid #000"}
                     [:text {:font "bold 16px Verdana, Helvetica, Arial, sans-serif"}]]]
   :css-include    [AttributeGraph]
   :initLocalState (fn [] {:graph-comm      (atom nil)
                           :select-resolver (fn [{::keys [resolvers]}]
                                              (let [{::keys [on-select-resolver]} (fp/get-computed (fp/props this))]
                                                (on-select-resolver (first resolvers))))})}
  (dom/div :.container
    (attribute-line-view header-view)

    (dom/div :.toolbar
      (dom/div
        (dom/label "Depth")
        (dom/input {:type     "number" :min 1 :value attr-depth
                    :onChange #(fm/set-integer! this ::attr-depth :event %)}))
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
        "Interconnections")
      (dom/label
        (dom/input {:type     "checkbox" :checked show-graph?
                    :onChange #(fm/set-value! this ::show-graph? (gobj/getValueByKeys % "target" "checked"))})
        "Graph"))
    (let [index (h/index-by ::pc/attribute attributes)]
      (dom/div :.graph
        (if (seq attr-reach-via)
          (dom/div :.data-list
            (dom/div :.data-header "Reach via")
            (for [[input v] (->> attr-reach-via
                                 (group-by (comp attr-path-key-root first))
                                 (sort-by (comp pr-str attr-path-key-root first)))
                  :let [direct? (some (comp direct-input? first) v)]
                  :when (or direct? nested-reaches?)]
              (dom/div
                (dom/div :.out-attr {:key   (pr-str input)
                                     :style (cond-> {} direct? (assoc :fontWeight "bold"))}
                  (dom/div (out-attribute-events this (if (= 1 (count input))
                                                        (first input)
                                                        input))
                    (pr-str input)))
                (if nested-reaches?
                  (for [[path resolvers] (->> v
                                              (map #(update % 0 (fn [x] (if (set? x) [x] x))))
                                              (sort-by (comp #(update % 0 (comp vec sort)) first)))
                        :let [path' (next path)]
                        :when path']
                    (dom/div {:key   (pr-str path)
                              :style {:marginLeft (str 10 "px")}}
                      (for [[k i] (map vector path' (range))]
                        (dom/div :.out-attr {:key   (pr-str k)
                                             :style {:marginLeft (str (* i 10) "px")}}
                          (dom/div (out-attribute-events this k)
                            (pr-str k)))))))))

            (if-let [form (si/safe-form attribute)]
              (dom/div
                (dom/div :.data-header "Spec")
                (pr-str form)

                (dom/div :.data-header "Examples")
                (try
                  (for [example (gen/sample (s/gen attribute))]
                    (dom/div (pr-str example)))
                  (catch :default _
                    (dom/div "Error generating samples")))))))
        (if show-graph?
          (let [shared-options {::direct-reaches?   direct-reaches?
                                ::nested-reaches?   nested-reaches?
                                ::direct-provides?  direct-provides?
                                ::nested-provides?  nested-provides?
                                ::interconnections? interconnections?}]
            (attribute-graph
              (merge {::attributes      (attribute-network
                                          (merge {::attr-depth attr-depth
                                                  ::attr-index index
                                                  ::attributes attributes}
                                            shared-options)
                                          attribute)
                      ::on-show-details on-select-attribute
                      ::on-click-edge   (fp/get-state this :select-resolver)
                      ::graph-comm      (fp/get-state this :graph-comm)}
                shared-options))))

        (if (seq attr-provides)
          (dom/div :.data-list-right
            (dom/div :.data-header "Provides")

            (dom/div :.provides-container
              (ex-tree/expandable-tree provides-tree
                {::ex-tree/root    provides-tree-source
                 ::ex-tree/render  (fn [{:keys [key]}]
                                     (dom/div (assoc (out-attribute-events this key)
                                                :classes [(-> (css/get-classnames AttributeView) :out-attr)])
                                       (pr-str key)))
                 ::ex-tree/sort-by :key}))
            #_
            (for [[_ v] (->> (group-by (comp attr-path-key-root first) attr-provides)
                             (sort-by (comp attr-path-key-root first)))]
              (for [[path resolvers] (->> v
                                          (map #(update % 0 (fn [x] (if (keyword? x) [x] x))))
                                          (remove #(and (not nested-provides?) (> (count (first %)) 1)))
                                          (sort-by first))
                    :let [k (peek path)]]
                (dom/div :.out-attr {:key   (pr-str path)
                                     :style {:marginLeft (str (* 10 (dec (count path))) "px")}}
                  (dom/div (out-attribute-events this k)
                    (pr-str k)))))))))))

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
                    [:.menu {:white-space  "nowrap"
                             :border-right "1px solid #000"
                             :overflow     "auto"}]]
   :css-include    [OutputAttributeView]
   :initLocalState (fn [] {:graph-comm      (atom nil)
                           :select-resolver (fn [{::keys [resolvers]}]
                                              (let [{::keys [on-select-resolver]} (fp/get-computed (fp/props this))]
                                                (on-select-resolver (first resolvers))))
                           :render          (fn [{:keys [key]}]
                                              (dom/div (assoc (out-attribute-events this key)
                                                         :classes [(:attribute (css/get-classnames ResolverView))])
                                                (pr-str key)))})}
  (let [input'         (if (= 1 (count input)) (first input) input)
        data           (-> this fp/get-reconciler fp/app-state deref (get-in (fp/get-ident this)))
        resolver-attrs (conj (out-all-attributes (->> output eql/query->ast)) input')
        attrs          (-> (h/index-by ::pc/attribute attributes)
                           (select-keys resolver-attrs)
                           (update input' assoc ::center? true)
                           vals)
        plugins        (-> (gobj/get this "context") ::plugins)]
    (dom/div :.container
      (dom/div :.header (str sym))
      (dom/div :.columns
        (dom/div :.menu
          (dom/div
            (dom/div :.data-header "Input")
            (dom/pre (h/pprint-str input)))
          (if output
            (dom/div
              (dom/div :.data-header "Output")
              (ex-tree/expandable-tree output-tree
                {::ex-tree/root    (eql/query->ast output)
                 ::ex-tree/render  (fp/get-state this :render)
                 ::ex-tree/sort-by :key})))

          (for [{::keys [plugin-id plugin-render-to-resolver-menu]} plugins
                :when plugin-render-to-resolver-menu]
            (dom/div {:key (pr-str plugin-id)}
              (plugin-render-to-resolver-menu data))))

        (attribute-graph {::attributes      attrs
                          ::graph-comm      (fp/get-state this :graph-comm)
                          ::on-show-details on-select-attribute
                          ::on-click-edge   (fp/get-state this :select-resolver)})))))

(gobj/set ResolverView "contextType" ExtensionContext)

(def resolver-view (fp/factory ResolverView {:keyfn ::pc/sym}))

(defn realize-references [state coll]
  (mapv #(get-in state %) coll))

(declare SearchEverything)

(def max-search-results 15)

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

(fp/defsc SearchEverything
  [this {::keys [text search-results]} computed]
  {:pre-merge      (fn [{:keys [current-normalized data-tree]}]
                     (merge
                       {::id             (random-uuid)
                        ::text           ""
                        ::search-results []}
                       current-normalized
                       data-tree))
   :ident          [::id ::id]
   :query          [::id ::text
                    {::search-results (fp/get-query AttributeLineView)}]
   :css            [[:.input {:display    "block"
                              :padding    "4px"
                              :outline    "none"
                              :border     "1px solid #233339"
                              :width      "100%"
                              :box-sizing "border-box"}]]
   :initLocalState (fn [] {::on-select-attribute
                           (fn [attr]
                             (if-let [orig (-> this fp/props fp/get-computed ::on-select-attribute)]
                               (orig attr))
                             (fp/transact! this [`(fm/set-props {::text           ""
                                                                 ::search-results []})]))})}
  (dom/div
    (dom/div
      (dom/input :.input
        {:type     "text"
         :value    text
         :onChange #(fp/transact! this [`(search {::text ~(h/target-value %)})])}))
    (if (seq search-results)
      (dom/div
        (for [item (take 20 search-results)]
          (attribute-line-view item
            (assoc computed
              ::highlight? true
              ::on-select-attribute (fp/get-state this ::on-select-attribute))))))))

(def search-everything (fp/computed-factory SearchEverything))

(fp/defsc AttributeMenu
  [this {::keys [attributes]} computed]
  {:pre-merge (fn [{:keys [current-normalized data-tree]}]
                (merge {} current-normalized data-tree))
   :ident     [::id ::id]
   :query     [::id {::attributes (fp/get-query AttributeLineView)}]
   :css       [[:.container {:overflow    "auto"
                             :max-height  "100%"
                             :width       "300px"
                             :background  "#cdc0b9"
                             :white-space "nowrap"
                             :padding     "10px"}]
               [:.attribute {:background  "#263238"
                             :display     "flex"
                             :font-family "'Open Sans'"
                             :padding     "6px 8px"
                             :font-size   "14px"
                             :margin      "6px 0"}
                [:a {:color           "#fff"
                     :text-decoration "none"}]]]}
  (dom/div :.container
    (for [attribute attributes]
      (attribute-line-view attribute computed))))

(def attribute-menu (fp/computed-factory AttributeMenu))

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
    (dom/div "Attribute count: " attribute-count)
    (dom/div "Resolver count: " resolver-count)
    (dom/div "Globals count: " globals-count)
    (dom/div "Idents count: " idents-count)
    (dom/div "Edges count: " attr-edges-count)
    (dom/h3 "Most Connected Attributes")
    (for [{::pc/keys [attribute]
           ::keys    [attr-edges-count]} top-connection-hubs]
      (simple-attribute {:react-key (pr-str attribute)
                         :onClick   #(on-select-attribute attribute)}
        (str "[" attr-edges-count "] " (pr-str attribute))))))

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
                          ;:ui/page {::id id}
                          :ui/page {::pc/sym 'abrams.controllers.graph.revolver/revolver-by-account}
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
   :css            [[:.container {:flex           "1"
                                  :display        "flex"
                                  :flex-direction "column"}]
                    [:.graph {:height  "800px"
                              :display "flex"
                              :border  "1px solid #000"}]]
   :css-include    [SimpleAttribute]
   :initLocalState (fn [] {:select-attribute #(fp/transact! this [`(navigate-to-attribute {::pc/attribute ~%})])
                           :select-resolver  #(fp/transact! this [`(navigate-to-resolver {::pc/sym ~%})])})}
  (dom/create-element (gobj/get ExtensionContext "Provider") #js {:value extensions}
    (dom/div :.container {:key "container"}
      (search-everything menu {::on-select-attribute (fp/get-state this :select-attribute)})
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
                            ::nested-provides? true})))))

(def index-explorer (fp/computed-factory IndexExplorer))
