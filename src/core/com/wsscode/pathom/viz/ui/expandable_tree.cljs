(ns com.wsscode.pathom.viz.ui.expandable-tree
  (:require [cljs.spec.alpha :as s]
            [com.wsscode.pathom.viz.helpers :as h]
            [fulcro.client.localized-dom :as dom]
            [fulcro.client.primitives :as fp]
            [ghostwheel.core :as g :refer [>defn >defn- >fdef => | <- ?]]))

(s/def ::path (s/coll-of any? :kind vector?))
(s/def ::expanded (s/coll-of ::path :kind set?))
(s/def ::expanded? boolean?)
(s/def ::render (s/fspec :args (s/cat :props map?) :ret any?))
(s/def ::key any?)
(s/def ::children (s/coll-of ::root))
(s/def ::root (s/keys :opt-un [::key ::children]))

(declare tree-item)

(fp/defsc TreeItem
  [this {:keys  [key children]
         ::keys [expanded expanded? path toggle-expanded render] :as node}]
  {:css [[:.item {:display     "flex"
                  :align-items "center"
                  :padding     "0 2px"}]
         [:.expander {:display      "flex"
                      :align-items  "center"
                      :color        "#656565"
                      :cursor       "pointer"
                      :font-size    "10px"
                      :margin-top   "1px"
                      :margin-right "3px"
                      :width        "10px"}]
         [:.children-container {:margin-left "13px"}]]}
  (dom/div {:key (pr-str key)}
    (if key
      (dom/div :.item
        (dom/div :.expander {:onClick #(toggle-expanded path)}
          (if children (if expanded? "▼" "▶")))
        (render node)))
    (if (or expanded? (not key))
      (dom/div {:classes [(if key :.children-container)]}
        (for [{:keys [key] :as node} children
              :let [path (conj path key)]]
          (tree-item
            (assoc node ::path path
                        ::expanded expanded
                        ::toggle-expanded toggle-expanded
                        ::render render
                        ::expanded? (contains? expanded path))))))))

(def tree-item (fp/factory TreeItem {:keyfn #(pr-str (:key %))}))

(fp/defsc ExpandableTree
  [this
   {::keys [expanded]}
   {::keys [render root]}]
  {:pre-merge      (fn [{:keys [current-normalized data-tree]}]
                     (merge
                       {:ui/id     (random-uuid)
                        ::expanded #{}}
                       current-normalized
                       data-tree))
   :ident          [:ui/id :ui/id]
   :query          [:ui/id ::expanded]
   :css-include    [TreeItem]
   :initLocalState (fn [] {:toggle-expanded (fn [path]
                                              (h/update-value! this ::expanded h/toggle-set-item path))})}
  (dom/div
    (tree-item
      (assoc root ::path []
                  ::expanded expanded
                  ::render render
                  ::toggle-expanded (fp/get-state this :toggle-expanded)))))

(def expandable-tree (fp/computed-factory ExpandableTree))
