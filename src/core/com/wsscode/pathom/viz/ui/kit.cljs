(ns com.wsscode.pathom.viz.ui.kit
  (:require [fulcro.client.primitives :as fp]
            [fulcro.client.localized-dom :as dom]
            [fulcro-css.css :as css]
            [ghostwheel.core :as g :refer [>defn >defn- >fdef => | <- ?]]))

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
  (dom/div :.container props (fp/children this)))

(def column (fp/factory Column))

(fp/defsc Row
  [this props]
  {:css [[:.container {:display   "flex"
                       :max-width "100%"}]]}
  (dom/div :.container props (fp/children this)))

(def row (fp/factory Row))

; endregion

; region components

(fp/defsc Panel
  [this {::keys [panel-title panel-tag]}]
  {}
  (dom/div :$panel
    (dom/p :$panel-heading$row-center
      (dom/span :$flex panel-title)
      (if panel-tag (dom/span :$tag$is-dark panel-tag)))
    (dom/div :$panel-block
      (dom/div :$scrollbars
        (fp/children this)))))

(def panel (fp/factory Panel))

; endregion

(fp/defsc UIKit [_ _]
  {:css         [[:.flex {:flex "1"}]
                 [:.scrollbars {:overflow "auto"}]]
   :css-include [Panel Column Row]})

(def ui-css (css/get-classnames UIKit))

(defn css
  "Get one class from the shared ui kit registry. You can send the class
  name as :some-class or :.some-class."
  [k]
  (or (get ui-css k)
      (get ui-css (keyword (subs (name k) 1)))))

(defn kc
  "Return a map pointing to the given classes.
  Eg: (kc :.flex :.scrollbars)"
  [& k]
  {:classes (mapv css k)})
