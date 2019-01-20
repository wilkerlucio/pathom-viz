(ns com.wsscode.pathom.viz.index-explorer
  (:require [com.wsscode.pathom.connect :as pc]
            [clojure.pprint]
            [fulcro.client.localized-dom :as dom]
            [fulcro.client.mutations :as fm]
            [fulcro.client.primitives :as fp]
            [edn-query-language.core :as eql]))

(defn pd [f]
  (fn [e]
    (.preventDefault e)
    (f e)))

(defn pprint-str [x]
  (with-out-str
    (clojure.pprint/pprint x)))

;; Views

(fp/defsc AttributeLineView
  [this {::pc/keys [attribute attribute-paths]
         ::keys    [global-attribute? ident-attribute?]}
   {::keys [on-select-attribute]}]
  {:pre-merge (fn [{:keys [current-normalized data-tree]}]
                (merge {} current-normalized data-tree))
   :ident     [::pc/attribute ::pc/attribute]
   :query     [::pc/attribute ::pc/attribute-paths ::global-attribute? ::ident-attribute?]
   :css       [[:.attribute {:background  "#263238"
                             :display     "flex"
                             :align-items "center"
                             :font-family "'Open Sans'"
                             :padding     "6px 8px"
                             :font-size   "14px"
                             :margin      "6px 0"}
                [:a {:color           "#fff"
                     :text-decoration "none"}]]
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
  (dom/div :.attribute
    (dom/a :.link {:href    "#"
                   :onClick (pd #(on-select-attribute attribute))}
      (str (pr-str attribute) " [" (count attribute-paths) "]"))
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
    (dom/a {:href "#" :onClick (pd #(on-select-attribute attr))}
      (pr-str attr))
    (pr-str attr)))

(fp/defsc AttributeView
  [this {::pc/keys [attribute attribute-paths]
         :>/keys [header-view]}
   {::keys  [on-select-resolver]
    :as     computed}]
  {:pre-merge (fn [{:keys [current-normalized data-tree]}]
                (merge
                  {:>/header-view {::pc/attribute (or (::pc/attribute data-tree)
                                                      (::pc/attribute current-normalized))}}
                  current-normalized
                  data-tree))
   :ident     [::pc/attribute ::pc/attribute]
   :query     [::pc/attribute ::pc/attribute-paths
               {:>/header-view (fp/get-query AttributeLineView)}]
   :css       [[:.container {:flex "1"}]
               [:.path {:margin-bottom "6px"}]]}
  (dom/div :.container
    (attribute-line-view header-view)
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
                (dom/a {:href "#" :onClick (pd #(on-select-resolver sym))}
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
    (dom/div :.title (attribute-link props key))
    ))

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
      (dom/pre (pprint-str input)))
    (if output
      (dom/div "Output: "
        (for [ast (-> output eql/query->ast :children)]
          (output-attribute-view ast))))))

(def resolver-view (fp/factory ResolverView {:keyfn ::pc/sym}))

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
  [this props computed]
  {:ident (fn [] (main-view-ident props))
   :query (fn []
            {::pc/attribute (fp/get-query AttributeView)
             ::pc/sym       (fp/get-query ResolverView)})}
  (let [props (fp/computed props computed)]
    (case (first (fp/get-ident this))
      ::pc/attribute (attribute-view props)
      ::pc/sym (resolver-view props)
      (dom/div "Blank page"))))

(def main-view-union (fp/computed-factory MainViewUnion {:keyfn #(or (::pc/attribute %) (::pc/sym %))}))

(defn process-index [{::pc/keys [index-oir index-resolvers idents]}]
  (let [attrs (->> index-oir
                   (reduce
                     (fn [attributes [attr paths]]
                       (update attributes attr merge
                         {::pc/attribute       attr
                          ::pc/attribute-paths paths
                          ::global-attribute?  (boolean (some (comp empty? first) paths))
                          ::ident-attribute?   (contains? idents attr)}))
                     {})
                   (vals)
                   (sort-by ::pc/attribute)
                   vec)]
    {::attributes attrs
     ::globals    (filterv ::global-attribute? attrs)
     ::idents     (filterv ::ident-attribute? attrs)

     ::resolvers  (->> index-resolvers
                       vals
                       (sort-by ::pc/sym)
                       vec)}))

;; Query

(fp/defsc AttributeIndex [_ _]
  {:ident [::pc/attribute ::pc/attribute]
   :query [::pc/attribute ::pc/attribute-paths]})

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
  [this {::keys   [index]
         :>/keys  [menu]
         :ui/keys [page]}]
  {:pre-merge      (fn [{:keys [current-normalized data-tree]}]
                     (merge
                       {::id     (random-uuid)
                        :ui/page {}}
                       current-normalized
                       data-tree
                       (if-let [index (get data-tree ::index)]
                         (process-index index))))
   :initial-state  {}
   :ident          [::id ::id]
   :query          [::id ::index
                    {:>/menu (fp/get-query AttributeMenu)}
                    {::attributes (fp/get-query AttributeIndex)}
                    {::globals (fp/get-query AttributeIndex)}
                    {::idents (fp/get-query AttributeIndex)}
                    {::resolvers (fp/get-query ResolverIndex)}
                    {:ui/page (fp/get-query MainViewUnion)}]
   :css            [[:.container {:display "flex"
                                  :flex    "1"}]]
   :initLocalState (fn [] {:select-attribute #(fp/transact! this [`(navigate-to-attribute {::pc/attribute ~%})])
                           :select-resolver  #(fp/transact! this [`(navigate-to-resolver {::pc/sym ~%})])})}
  (dom/div :.container
    (attribute-menu menu {::on-select-attribute (fp/get-state this :select-attribute)})
    (if page
      (main-view-union page (assoc index
                              ::on-select-attribute (fp/get-state this :select-attribute)
                              ::on-select-resolver (fp/get-state this :select-resolver))))))

(def index-explorer (fp/factory IndexExplorer))
