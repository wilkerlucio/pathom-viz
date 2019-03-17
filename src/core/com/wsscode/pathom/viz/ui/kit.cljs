(ns com.wsscode.pathom.viz.ui.kit
  (:require [fulcro.client.primitives :as fp]
            [fulcro.client.localized-dom :as dom]
            [fulcro-css.css :as css]
            [goog.object :as gobj]
            [ghostwheel.core :as g :refer [>defn >defn- >fdef => | <- ?]]
            [cljs.spec.alpha :as s]))

(declare gc css ccss)

; region helpers

(>defn props [props]
  [map? => map?]
  (into {} (remove (comp qualified-keyword? first)) props))

; endregion

; region basics

(fp/defsc Button
  [this props]
  {:css [[:.container {:border "1px solid #000"}]]}
  (dom/div :.container props (fp/children this)))

(def button (fp/factory Button))

(fp/defsc Column
  [this props]
  {:css [[:.container {:display        "flex"
                       :flex-direction "column"
                       :max-height     "100%"}]]}
  (dom/div :.container props
    (fp/children this)))

(def column (fp/factory Column))

(fp/defsc Row
  [this props]
  {:css [[:.container {:display   "flex"
                       :max-width "100%"}]
         [:.center {:align-items "center"}]]}
  (dom/div :.container props (fp/children this)))

(def row (fp/factory Row))

; endregion

; region components

(fp/defsc Panel
  [this {::keys [panel-title panel-tag scrollbars?]
         :or    {scrollbars? true}
         :as    p}]
  {}
  (dom/div :$panel (props p)
    (dom/p :$panel-heading$row-center
      (dom/span (gc :.flex) panel-title)
      (if panel-tag (dom/span :$tag$is-dark panel-tag)))
    (dom/div :$panel-block
      (if scrollbars?
        (dom/div :$scrollbars
          (fp/children this))
        (fp/children this)))))

(def panel (fp/factory Panel))

(s/def ::title string?)
(s/def ::collapsed? boolean?)
(s/def ::on-toggle (s/fspec :args (s/cat :active? boolean?)))

(fp/defsc CollapsibleBox
  [this {::keys [collapsed? on-toggle title]
         :or    {on-toggle identity}
         :as    p}]
  {:css [[:.container {:cursor "pointer"}]
         [:.header {:background "#f3f3f3"
                    :color      "#717171"
                    :padding    "1px 0"}]
         [:.arrow {:padding   "0 4px"
                   :font-size "11px"}]]}
  (dom/div :.container (props p)
    (row {:classes (into [:.center] (ccss this :.header))
          :onClick #(on-toggle (not collapsed?))}
      (dom/div :.arrow (if collapsed? "▶" "▼"))
      (dom/div (gc :.flex) title))
    (apply dom/div {:style {:display (if collapsed? "none")}}
      (fp/children this))))

(def collapsible-box (fp/factory CollapsibleBox {:keyfn :ui/id}))

; endregion

(fp/defsc UIKit [_ _]
  {:css         [[:.flex {:flex "1"}]
                 [:.scrollbars {:overflow "auto"}]
                 [:.no-scrollbars {:overflow "hidden"}]]
   :css-include [Panel Column Row CollapsibleBox]})

(def ui-css (css/get-classnames UIKit))

(defn get-css [map k]
  (or (get map k)
      (get map (keyword (subs (name k) 1)))))

(defn css
  "Get one class from the shared ui kit registry. You can send the class
  name as :some-class or :.some-class."
  [k]
  (get-css ui-css k))

(defn gc
  "Return a map pointing to the given global classes.
  Eg: (kc :.flex :.scrollbars)"
  [& k]
  {:classes (mapv css k)})

(defn ccss [component & k]
  (if-let [css-map (try
                     (some-> component (gobj/get "constructor") css/get-classnames)
                     (catch :default _ nil))]
    (into [] (map (partial get-css css-map)) k)
    []))
