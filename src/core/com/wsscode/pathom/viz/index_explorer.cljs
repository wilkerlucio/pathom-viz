(ns com.wsscode.pathom.viz.index-explorer
  (:require ["./d3-attribute-graph" :as d3attr]
            ["./detect-element-size" :refer [addResizeListener]]
            [com.wsscode.fuzzy :as fuzzy]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.viz.helpers :as h]
            [goog.object :as gobj]
            [edn-query-language.core :as eql]
            [fulcro.client.localized-dom :as dom]
            [fulcro.client.mutations :as fm]
            [fulcro.client.primitives :as fp]))

;; Views

(defn node-radius [{::keys [weight reach]}]
  (js/Math.round
    (+
      (js/Math.sqrt (+ (or weight 1) 2))
      (js/Math.sqrt (+ (or reach 1) 1)))))

(defn attribute->node [{::pc/keys [attribute]
                        ::keys    [weight reach center?]
                        :as       attr}]
  {:attribute (pr-str attribute)
   :mainNode  center?
   :weight    weight
   :reach     reach
   :radius    (node-radius attr)})

(defn direct-input? [input] (set? input))
(defn nested? [input] (vector? input))

(defn single-input [input]
  (let [input (if (nested? input) (first input) input)]
    (and (= 1 (count input)) (first input))))

(defn global-input? [input]
  (and (direct-input? input) (empty? input)))

(defn compute-nodes-links [{::keys [attributes nested-provides? nested-reaches?]}]
  (let [index       (h/index-by ::pc/attribute attributes)
        attributes' (filter (comp #(or (keyword? %) (= % #{})) ::pc/attribute) attributes)]
    {:nodes (mapv attribute->node attributes')
     :links (mapcat
              (fn [{::pc/keys [attribute attr-provides attr-reach-via]}]
                (let [attr-str (pr-str attribute)]
                  (let [res (-> []
                                (into
                                  (keep (fn [[provided]]
                                          (if (nested? provided)
                                            (when (and nested-provides?
                                                       (contains? index (peek provided))
                                                       (not= attribute (peek provided)))
                                              {:source      attr-str
                                               :deep        true
                                               :lineProvide true
                                               :target      (pr-str (peek provided))})
                                            (when (and (contains? index provided)
                                                       (not= attribute provided))
                                              {:source      attr-str
                                               :lineProvide true
                                               :target      (pr-str provided)}))))
                                  attr-provides)
                                (into
                                  (keep (fn [[input]]
                                          (cond
                                            #_#_(global-input? input)
                                                {:source (pr-str input)
                                                 :target attr-str}

                                            (and (single-input input)
                                                 (contains? index (single-input input))
                                                 (not= attribute (single-input input))
                                                 (or (and (nested? input) nested-reaches?)
                                                     (not (nested? input))))
                                            {:source    (pr-str (single-input input))
                                             :deep      (nested? input)
                                             :lineReach true
                                             :target    attr-str})))
                                  attr-reach-via))]
                    res)))
              attributes')}))

(defn merge-attr [a b]
  (merge a b))

(defn render-attribute-graph [this]
  (let [{::keys [on-show-details graph-comm] :as props} (-> this fp/props)
        on-show-details (or on-show-details identity)
        current         (gobj/get this "renderedData")
        container       (gobj/get this "svgContainer")
        svg             (gobj/get this "svg")]
    (if current ((gobj/get current "dispose")))
    (gobj/set svg "innerHTML" "")
    (js/console.log (into [] (map (fn [[k v]] [k (count v)])) (compute-nodes-links props)))
    (let [render-settings (d3attr/render svg
                            (clj->js {:svgWidth    (gobj/get container "clientWidth")
                                      :svgHeight   (gobj/get container "clientHeight")
                                      :data        (compute-nodes-links props)
                                      :showDetails (fn [attr d js]
                                                     (on-show-details (keyword (subs attr 1)) d js))}))]
      (if graph-comm (reset! graph-comm render-settings))
      (gobj/set this "renderedData" render-settings))))

(fp/defsc AttributeGraph
  [this {::keys []}]
  {:css
   [[:.container {:flex      1
                  :max-width "100%"}
     [:$pathom-viz-index-explorer-attr-node
      {;:stroke "#f34545b3"
       :fill "#000A"}
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
       :stroke-width   "1px"
       :fill           "none"}

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

(defn simple-attr [index attr]
  (-> index (get attr)))

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
            (if (or (and direct-reaches? (direct-input? input) (single-input input))
                    (and nested-reaches? (nested? input) (single-input input)))
              (let [attr (single-input input)]
                (if (> attr-depth 1)
                  (attribute-network*
                    (assoc options' ::sub-index out)
                    attr)
                  (update out attr merge (simple-attr index attr))))
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
                (update out attr merge (simple-attr index attr)))

              (and nested-provides? (nested? attr))
              (let [attr (peek attr)]
                (update out attr merge (simple-attr index attr)))

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
   :css       [[:.container {:color       "#9a45b1"
                             :cursor      "pointer"
                             :font-family "sans-serif"
                             :font-size   "14px"
                             :line-height "1.4em"
                             :padding     "0 2px"}]]}
  (apply dom/div :.container props (fp/children this)))

(def simple-attribute (fp/factory SimpleAttribute))

(fp/defsc AttributeView
  [this {::pc/keys [attribute-paths attribute attr-reach-via attr-provides]
         ::keys    [attr-depth direct-reaches? nested-reaches? direct-provides? nested-provides?]
         :>/keys   [header-view]}
   {::keys [on-select-resolver on-select-attribute attributes]
    :as    computed}]
  {:pre-merge      (fn [{:keys [current-normalized data-tree]}]
                     (let [attr (or (::pc/attribute data-tree)
                                    (::pc/attribute current-normalized))]
                       (merge
                         {::attr-depth       1
                          ::direct-reaches?  true
                          ::nested-reaches?  false
                          ::direct-provides? true
                          ::nested-provides? false
                          :>/header-view     {::pc/attribute attr}}
                         current-normalized
                         data-tree)))
   :ident          [::pc/attribute ::pc/attribute]
   :query          [::pc/attribute ::pc/attribute-paths ::attr-depth ::direct-reaches? ::nested-reaches?
                    ::direct-provides? ::nested-provides? ::pc/attr-reach-via ::pc/attr-provides
                    {::attr-provides [::pc/attribute]}
                    {:>/header-view (fp/get-query AttributeLineView)}]
   :css            [[:.container {:flex           "1"
                                  :flex-direction "column"
                                  :display        "flex"}]
                    [:.toolbar {:display               "grid"
                                :grid-template-columns "repeat(5, max-content)"
                                :grid-gap              "10px"}]
                    [:.data-list {:white-space  "nowrap"
                                  :border-right "1px solid #000"
                                  :overflow     "auto"}]
                    [:.data-header {:padding     "9px 4px"
                                    :font-weight "bold"
                                    :font-family "Verdana"}]
                    [:.out-attr {:padding     "0 2px"
                                 :color       "#9a45b1"
                                 :cursor      "pointer"
                                 :font-family "sans-serif"
                                 :font-size   "14px"
                                 :line-height "1.4em"}]
                    [:.path {:margin-bottom "6px"}]
                    [:.graph {:display "flex"
                              :flex    "1"
                              :border  "1px solid #000"}
                     [:text {:font "bold 16px Verdana, Helvetica, Arial, sans-serif"}]]]
   :css-include    [AttributeGraph]
   :initLocalState (fn [] {:graph-comm (atom nil)})}
  (dom/div :.container
    (attribute-line-view header-view)
    (dom/div :.toolbar
      (dom/div
        (dom/label "Depth")
        (dom/input {:type     "number" :min 1 :max 5 :value attr-depth
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
        "Nested outputs"))
    (let [index (h/index-by ::pc/attribute attributes)]
      (dom/div :.graph
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
                (dom/div {:onClick      #(on-select-attribute (if (= 1 (count input))
                                                                (first input)
                                                                input))
                          :onMouseEnter #(if-let [settings @(fp/get-state this :graph-comm)]
                                           ((gobj/get settings "highlightNode") (str (first input))))
                          :onMouseLeave #(if-let [settings @(fp/get-state this :graph-comm)]
                                           ((gobj/get settings "unhighlightNode") (str (first input))))}
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
                        (dom/div {:onClick      #(on-select-attribute k)
                                  :onMouseEnter #(if-let [settings @(fp/get-state this :graph-comm)]
                                                   ((gobj/get settings "highlightNode") (str k)))
                                  :onMouseLeave #(if-let [settings @(fp/get-state this :graph-comm)]
                                                   ((gobj/get settings "unhighlightNode") (str k)))}
                          (pr-str k)))))))))
          (dom/div :.data-header "Provides")
          (for [[_ v] (->> (group-by (comp attr-path-key-root first) attr-provides)
                           (sort-by (comp attr-path-key-root first)))]
            (for [[path resolvers] (->> v
                                        (map #(update % 0 (fn [x] (if (keyword? x) [x] x))))
                                        (remove #(and (not nested-provides?) (> (count (first %)) 1)))
                                        (sort-by first))
                  :let [k (peek path)]]
              (dom/div :.out-attr {:key   (pr-str path)
                                   :style {:marginLeft (str (* 10 (dec (count path))) "px")}}
                (dom/div {:onClick      #(on-select-attribute k)
                          :onMouseEnter #(if-let [settings @(fp/get-state this :graph-comm)]
                                           ((gobj/get settings "highlightNode") (str k)))
                          :onMouseLeave #(if-let [settings @(fp/get-state this :graph-comm)]
                                           ((gobj/get settings "unhighlightNode") (str k)))}
                  (pr-str k))))))
        (attribute-graph {::attributes       (attribute-network {::attr-depth       attr-depth
                                                                 ::attr-index       index
                                                                 ::attributes       attributes
                                                                 ::direct-reaches?  direct-reaches?
                                                                 ::nested-reaches?  nested-reaches?
                                                                 ::direct-provides? direct-provides?
                                                                 ::nested-provides? nested-provides?} attribute)
                          ::graph-comm       (fp/get-state this :graph-comm)
                          ::direct-reaches?  direct-reaches?
                          ::nested-reaches?  nested-reaches?
                          ::direct-provides? direct-provides?
                          ::nested-provides? nested-provides?
                          ::on-show-details  on-select-attribute})))
    (dom/div
      (for [[input resolvers] attribute-paths]
        (dom/div :.path {:key (pr-str input)}
          "#{"
          (for [attr input]
            (dom/span {:key (pr-str attr)}
              (attribute-link computed attr)
              ", "))
          "}"
          (dom/div
            "#{"
            (for [sym resolvers]
              (dom/span {:key (pr-str sym)}
                (dom/a {:href "#" :onClick (h/pd #(on-select-resolver sym))}
                  (pr-str sym))
                ", "))
            "}"))))))

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

(fp/defsc ResolverView
  [this {::pc/keys [sym input output]}
   {::pc/keys [index-oir]
    ::keys    [on-select-attribute on-select-resolver]
    :as       computed}]
  {:pre-merge   (fn [{:keys [current-normalized data-tree]}]
                  (merge
                    {}
                    current-normalized
                    data-tree))
   :ident       [::pc/sym ::pc/sym]
   :query       [::pc/sym ::pc/input ::pc/output]
   :css         [[:.container {:flex "1"}]]
   :css-include [OutputAttributeView]}
  (dom/div :.container
    "Resolver: " (pr-str sym)
    (dom/div "Input: "
      (dom/pre (h/pprint-str input)))
    (if output
      (dom/div "Output: "
        (for [ast (-> output eql/query->ast :children)]
          (output-attribute-view ast))))))

(def resolver-view (fp/factory ResolverView {:keyfn ::pc/sym}))

(defn realize-references [state coll]
  (mapv #(get-in state %) coll))

(declare SearchEverything)

(def max-search-results 15)

(fm/defmutation search [{::keys [text]}]
  (action [{:keys [ref state reconciler]}]
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
                 top-connection-hubs]}
   {::keys [on-select-attribute]}]
  {:pre-merge (fn [{:keys [current-normalized data-tree]}]
                (merge {} current-normalized data-tree))
   :ident     [::id ::id]
   :query     [::id ::attribute-count ::resolver-count ::globals-count ::idents-count
               {::top-connection-hubs [::pc/attribute ::attr-edges-count]}]}
  (fp/fragment
    "Show some data here"
    (dom/div "Attribute count: " attribute-count)
    (dom/div "Resolver count: " resolver-count)
    (dom/div "Globals count: " globals-count)
    (dom/div "Idents count: " idents-count)
    (dom/h3 "Top Attributes")
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
         :ui/keys [menu page]}]
  {:pre-merge      (fn [{:keys [current-normalized data-tree]}]
                     (merge
                       (let [id (or (::id data-tree)
                                    (::id current-normalized)
                                    (random-uuid))]
                         {::id     id
                          :ui/menu {::id id}
                          :ui/page {::id id}})
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
  (dom/div :.container
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
                          ::nested-provides? true}))))

(def index-explorer (fp/factory IndexExplorer))
