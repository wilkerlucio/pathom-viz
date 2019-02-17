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

(defn attribute->node [{::pc/keys [attribute]
                        ::keys    [weight reach]}]
  {:attribute (pr-str attribute)
   :weight    weight
   :reach     reach})

(defn single-input? [input]
  (and (set? input) (= 1 (count input))))

(defn global-input? [input]
  (and (set? input) (empty? input)))

(defn compute-nodes-links [{::keys [attributes]}]
  (let [index       (h/index-by ::pc/attribute attributes)
        attributes' (filter (comp #(or (keyword? %) (= % #{})) ::pc/attribute) attributes)]
    {:nodes (mapv attribute->node attributes')
     :links (mapcat
              (fn [{::pc/keys [attribute attr-provides attr-reach-via]}]
                (let [attr-str (pr-str attribute)]
                  (let [res (-> []
                                (into
                                  (keep (fn [[provided]]
                                          (if (vector? provided)
                                            (when (contains? index (peek provided))
                                              {:source attr-str
                                               :deep   true
                                               :target (pr-str (peek provided))})
                                            (when (contains? index provided)
                                              {:source attr-str
                                               :target (pr-str provided)}))))
                                  attr-provides)
                                (into
                                  (keep (fn [[input]]
                                          (cond
                                            #_#_(global-input? input)
                                                {:source (pr-str input)
                                                 :target attr-str}

                                            (and (single-input? input)
                                                 (contains? index (first input)))
                                            {:source (pr-str (first input))
                                             :target attr-str})))
                                  attr-reach-via))]
                    res)))
              attributes')}))

(defn merge-attr [a b]
  (merge a b))

(defn render-attribute-graph [this]
  (let [{::keys [on-show-details] :as props} (-> this fp/props)
        on-show-details (or on-show-details identity)
        container       (gobj/get this "svgContainer")
        svg             (gobj/get this "svg")]
    (gobj/set svg "innerHTML" "")
    (js/console.log (into [] (map (fn [[k v]] [k (count v)])) (compute-nodes-links props)))
    (gobj/set this "renderedData"
      (d3attr/render svg
        (clj->js {:svgWidth    (gobj/get container "clientWidth")
                  :svgHeight   (gobj/get container "clientHeight")
                  :data        (compute-nodes-links props)
                  :showDetails (fn [attr d js]
                                 (on-show-details (keyword (subs attr 1)) d js))})))))

(fp/defsc AttributeGraph
  [this {::keys []}]
  {:css
   [[:.container {:flex      1
                  :max-width "100%"}
     [:$pathom-viz-index-explorer-attr-node
      {:stroke "#f34545b3"
       :fill   "#000A"}]
     [:$pathom-viz-index-explorer-attr-link
      {:stroke         "#999"
       :stroke-opacity "0.6"
       :stroke-width   "1px"}]
     [:$pathom-viz-index-explorer-attr-link-indirect
      {:stroke           "#999"
       :stroke-opacity   "0.6"
       :stroke-width     "1px"
       :stroke-dasharray "3px"}]
     [:text {:font "bold 18px Verdana, Helvetica, Arial, sans-serif"}]]]

   :componentDidMount
   (fn []
     (render-attribute-graph this)
     #_(addResizeListener (gobj/get this "svgContainer") #(recompute-trace-size this)))

   :componentDidUpdate
   (fn [prev-props _]
     (when (not= prev-props (-> this fp/props))
       (render-attribute-graph this)))

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
   {::keys [on-select-attribute highlight?]}]
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
  [{::keys [attr-depth attributes sub-index attr-index attr-visited]
    :or    {attr-depth   1
            sub-index    {}
            attr-visited #{}}
    :as    options} source]
  (if (contains? attr-visited source)
    sub-index
    (let [index    (h/index-by ::pc/attribute attributes)
          base     (merge sub-index (select-keys index [source]))
          {::pc/keys [attr-reach-via attr-provides]} (get index source)
          options' (assoc options ::attr-index (or attr-index index)
                                  ::attr-depth (dec attr-depth)
                                  ::attr-visited (conj attr-visited source))]
      (as-> base <>
        (reduce
          (fn [out input]
            (if (single-input? input)
              (let [attr (first input)]
                (if (> attr-depth 1)
                  (attribute-network*
                    (assoc options' ::sub-index out)
                    attr)
                  (update out attr merge (simple-attr index attr))))
              out))
          <>
          (keys attr-reach-via))
        (reduce
          (fn [out attr]
            (cond
              (keyword? attr)
              (if (> attr-depth 1)
                (attribute-network*
                  (assoc options' ::sub-index out)
                  attr)
                (update out attr merge (simple-attr index attr)))

              #_#_(vector? attr)
                  (let [attr (peek attr)]
                    (update out attr merge (simple-attr index attr)))

              :else
              out))
          <>
          (keys attr-provides))))))

(defn attribute-network [options source]
  (vals (attribute-network* options source)))

(fp/defsc AttributeView
  [this {::pc/keys [attribute-paths attribute]
         ::keys    [attr-provides attr-depth]
         :>/keys   [header-view]
         :as       props}
   {::keys [on-select-resolver on-select-attribute attributes]
    :as    computed}]
  {:pre-merge   (fn [{:keys [current-normalized data-tree]}]
                  (merge
                    {::attr-depth   1
                     :>/header-view {::pc/attribute (or (::pc/attribute data-tree)
                                                        (::pc/attribute current-normalized))}}
                    current-normalized
                    data-tree))
   :ident       [::pc/attribute ::pc/attribute]
   :query       [::pc/attribute ::pc/attribute-paths ::attr-depth
                 {::attr-provides [::pc/attribute]}
                 {:>/header-view (fp/get-query AttributeLineView)}]
   :css         [[:.container {:flex     "1"
                               :overflow "auto"}]
                 [:.path {:margin-bottom "6px"}]
                 [:.graph {:height  "500px"
                           :display "flex"
                           :border  "1px solid #000"}
                  [:text {:font "bold 16px Verdana, Helvetica, Arial, sans-serif"}]]]
   :css-include [AttributeGraph]}
  (dom/div :.container
    (attribute-line-view header-view)
    (do (def *attributes attributes) nil)
    (dom/input {:type     "number" :min 1 :max 5 :value attr-depth
                :onChange #(fm/set-integer! this ::attr-depth :event %)})
    (dom/div :.graph
      (attribute-graph {::attributes      (attribute-network {::attr-depth attr-depth
                                                              ::attributes attributes} attribute)
                        ::on-show-details on-select-attribute}))
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
            "}"))))
    (dom/div
      (dom/div "Provides:")
      (for [{::pc/keys [attribute]} attr-provides]
        (dom/div {:key (pr-str attribute)}
          (attribute-link computed attribute))))))

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

(fm/defmutation search [{::keys [text]}]
  (action [{:keys [ref state reconciler]}]
    (let [attributes (->> (get-in @state (conj ref ::attributes))
                          (realize-references @state)
                          (mapv #(assoc % ::fuzzy/string (pr-str (::pc/attribute %)))))
          fuzzy-res  (if (> (count text) 2)
                       (fuzzy/fuzzy-match {::fuzzy/options      attributes
                                           ::fuzzy/search-input text})
                       [])]
      (swap! state fp/merge-component SearchEverything (into {::search-results (vec fuzzy-res)} [ref]))
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

(defn main-view-ident [{::pc/keys [sym attribute]}]
  (cond
    sym [::pc/sym sym]
    attribute [::pc/attribute attribute]
    :else [:invalid "ident"]))

(fp/defsc MainViewUnion
  [this props]
  {:ident (fn [] (main-view-ident props))
   :query (fn []
            {::pc/attribute (fp/get-query AttributeView)
             ::pc/sym       (fp/get-query ResolverView)})}
  (case (first (fp/get-ident this))
    ::pc/attribute (attribute-view props)
    ::pc/sym (resolver-view props)
    (dom/div "Blank page")))

(def main-view-union (fp/computed-factory MainViewUnion {:keyfn #(or (::pc/attribute %) (::pc/sym %))}))

(defn process-index [{::pc/keys [index-resolvers idents index-attributes]}]
  (let [attrs (->> index-attributes
                   (map (fn [[attr {::pc/keys [attr-reach-via attr-provides] :as data}]]
                          (assoc data
                            ::weight (count attr-provides)
                            ::reach (count attr-reach-via)
                            ::pc/attribute attr
                            ::global-attribute? (contains? attr-reach-via #{})
                            ::ident-attribute? (contains? idents attr))))
                   (sort-by (comp pr-str ::pc/attribute))
                   (vec))]
    {::attributes attrs
     ::globals    (filterv ::global-attribute? attrs)
     ::idents     (filterv ::ident-attribute? attrs)

     ::resolvers  (->> index-resolvers
                       vals
                       (sort-by ::pc/sym)
                       vec)
     ; TODO remove me
     :ui/page     {::pc/attribute :customer/cpf}}))

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
                          :ui/page {}})
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
                    {::resolvers (fp/get-query ResolverIndex)}
                    {:ui/page (fp/get-query MainViewUnion)}]
   :css            [[:.container {:flex "1"}]
                    [:.graph {:height  "800px"
                              :display "flex"
                              :border  "1px solid #000"}]]
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
        (attribute-graph {::attributes attributes}))))

(def index-explorer (fp/factory IndexExplorer))
