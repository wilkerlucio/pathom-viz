(ns com.wsscode.pathom.viz.index-explorer
  (:require [com.wsscode.pathom.connect :as pc]
            [clojure.pprint]
            [fulcro.client.localized-dom :as dom]
            [fulcro.client.mutations :as fm]
            [fulcro.client.primitives :as fp]))

(defn pd [f]
  (fn [e]
    (.preventDefault e)
    (f e)))

(defn pprint-str [x]
  (with-out-str
    (clojure.pprint/pprint x)))

(fp/defsc AttributeView
  [this {::pc/keys [attribute attribute-paths]}
   {::pc/keys [index-oir]
    ::keys    [on-select-attribute on-select-resolver]}]
  {:pre-merge     (fn [{:keys [current-normalized data-tree]}]
                    (merge {} current-normalized data-tree))
   :initial-state {}
   :ident         [::pc/attribute ::pc/attribute]
   :query         [::pc/attribute ::pc/attribute-paths]
   :css           [[:.container {:flex "1"}]
                   [:.path {:margin-bottom "6px"}]]}
  (dom/div :.container
    (dom/div (pr-str attribute))
    (dom/div
      (for [[input resolvers] attribute-paths]
        (dom/div :.path {:key (pr-str input)}
          "#{"
          (for [attr input]
            (dom/span {:key (pr-str attr)}
              (if (contains? index-oir attr)
                (dom/a {:href "#" :onClick (pd #(on-select-attribute attr))}
                  (pr-str attr))
                (pr-str attr))
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

(fp/defsc ResolverView
  [this {::pc/keys [sym input output]}]
  {:pre-merge     (fn [{:keys [current-normalized data-tree]}]
                    (merge {} current-normalized data-tree))
   :initial-state {}
   :ident         [::pc/sym ::pc/sym]
   :query         [::pc/sym ::pc/input ::pc/output]}
  (dom/div
    "Resolver: " (pr-str sym)
    (dom/div "Input: "
      (dom/pre (pprint-str input)))
    (dom/div "Output: "
      (dom/pre (pprint-str output)))))

(def resolver-view (fp/factory ResolverView {:keyfn ::pc/sym}))

(fp/defsc AttributeMenu
  [this {::keys [attributes]} {::keys [on-select-attribute]}]
  {:pre-merge (fn [{:keys [current-normalized data-tree]}]
                (merge {} current-normalized data-tree))
   :ident     [::id ::id]
   :query     [::id {::attributes (fp/get-query AttributeView)}]
   :css       [[:.container {:overflow   "auto"
                             :max-height "100%"
                             :background "#cdc0b9"}]]}
  (dom/div :.container
    (for [{::pc/keys [attribute attribute-paths]} attributes]
      (dom/div {:key (str attribute)}
        (dom/a {:href    "#"
                :onClick (pd #(on-select-attribute attribute))}
          (str (pr-str attribute) " [" (count attribute-paths) "]"))))))

(def attribute-menu (fp/computed-factory AttributeMenu))

(defn process-index [{::pc/keys [index-oir index-resolvers]}]
  {::attributes (->> index-oir
                     (reduce
                       (fn [attrs [k v]]
                         (update attrs k merge
                           {::pc/attribute       k
                            ::pc/attribute-paths v}))
                       {})
                     (vals)
                     (sort-by ::pc/attribute)
                     vec)

   ::resolvers  (->> index-resolvers
                     vals
                     (sort-by ::pc/sym)
                     vec)})

(fp/defsc ResolverIndex [_ _]
  {:ident [::pc/sym ::pc/sym]
   :query [::pc/sym ::pc/input ::pc/output ::pc/params]})

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
   :ident          [::id ::id]
   :query          [::id ::index
                    {:>/menu (fp/get-query AttributeMenu)}
                    {::attributes (fp/get-query AttributeView)}
                    {::resolvers (fp/get-query ResolverIndex)}
                    {:ui/page (fp/get-query MainViewUnion)}]
   :css            [[:.container {:display "flex"
                                  :flex    "1"}]]
   :initLocalState (fn [] {:select-attribute #(fm/set-value! this :ui/page [::pc/attribute %])
                           :select-resolver  #(fm/set-value! this :ui/page [::pc/sym %])})}
  (dom/div :.container
    (attribute-menu menu {::on-select-attribute (fp/get-state this :select-attribute)})
    (if page
      (main-view-union page (assoc index
                              ::on-select-attribute (fp/get-state this :select-attribute)
                              ::on-select-resolver (fp/get-state this :select-resolver))))))

(def index-explorer (fp/factory IndexExplorer))
