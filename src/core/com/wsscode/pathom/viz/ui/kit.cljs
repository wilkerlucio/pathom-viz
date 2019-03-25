(ns com.wsscode.pathom.viz.ui.kit
  (:require [fulcro.client.primitives :as fp]
            [fulcro.client.localized-dom :as dom]
            [fulcro-css.css :as css]
            [goog.object :as gobj]
            [goog.string :as gstr]
            [ghostwheel.core :as g :refer [>defn >defn- >fdef => | <- ?]]
            [cljs.spec.alpha :as s]
            [fulcro.client.dom :as domc]))

(declare gc css ccss)

; region variables

(def font-base "BlinkMacSystemFont,-apple-system,\"Segoe UI\",Roboto,Oxygen,Ubuntu,Cantarell,\"Fira Sans\",\"Droid Sans\",\"Helvetica Neue\",Helvetica,Arial,sans-serif")
(def font-code "monospace!important")

(def text-base {:font-family font-base
                :line-height "1.5"})

(def css-header
  {:margin "0"
   :font-size "2rem"
   :font-weight "600"})

(def no-user-select
  {:-webkit-touch-callout "none"
   :-webkit-user-select   "none"
   :-moz-user-select      "none"
   :-ms-user-select       "none"
   :user-select           "none"})

; endregion

; region helpers

(def mergers
  {:classes (fn [a b]
              (into a b))})

(s/def ::merger-map (s/map-of keyword? fn?))

(>defn merge-with-mergers
  [mergers a b]
  [::merger-map map? map? => map?]
  (persistent!
    (reduce-kv
      (fn [a k v]
        (if (and (contains? a k)
                 (contains? mergers k))
          (assoc! a k ((get mergers k) (get a k) v))
          (assoc! a k v)))
      (transient a)
      b)))

(>defn dom-props
  ([props]
   [map? => map?]
   (into {} (remove (comp qualified-keyword? first)) props))
  ([default props']
   [map? map? => map?]
   (dom-props (merge-with-mergers mergers default props'))))

(>defn event-value [e]
  [any? => string?]
  (gobj/getValueByKeys e "target" "value"))

; endregion

; region basics

(fp/defsc Button
  [this props]
  {:css [[:.button {:cursor "pointer"}
          [:&:disabled {:cursor "default"}]]]}
  (dom/button :.button props (fp/children this)))

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

(fp/defsc Tag
  [this props]
  {:css [[:.tag {:align-items      "center"
                 :background-color "#f5f5f5"
                 :border-radius    "4px"
                 :color            "#4a4a4a"
                 :display          "inline-flex"
                 :font-size        ".75rem"
                 :height           "2em"
                 :justify-content  "center"
                 :line-height      "1.5"
                 :padding-left     ".75em"
                 :padding-right    ".75em"
                 :white-space      "nowrap"}
          [:&.is-family-code
           {:font-family font-code
            :font-size   "0.9rem"}]

          [:&.is-primary
           {:background-color "#00d1b2"
            :color            "#fff"}]
          [:&.is-link
           {:background-color "#3273dc"
            :color "#fff"}]
          [:&.is-dark
           {:background-color "#363636"
            :color            "#f5f5f5"}]]]}
  (dom/span :.tag (dom-props props) (fp/children this)))

(def tag (fp/factory Tag))

(fp/defsc PanelBlock
  [this {::keys [scrollbars?]
         :or    {scrollbars? false}
         :as    props}]
  {:css [[:.panel-block
          {:border-bottom "1px solid #dbdbdb"
           :border-left   "1px solid #dbdbdb"
           :border-right  "1px solid #dbdbdb"}

          {:align-items     "center"
           :color           "#363636"
           :display         "flex"
           :line-height     "1.5"
           :justify-content "flex-start"
           :padding         ".5em .75em"}

          text-base]]}
  (dom/div :.panel-block (dom-props props)
    (if scrollbars?
      (dom/div (gc :.scrollbars)
        (fp/children this))
      (fp/children this))))

(def panel-block (fp/factory PanelBlock))

(s/def ::panel-title string?)
(s/def ::panel-tag (s/or :string string? :number number?))
(s/def ::scrollbars? boolean?)
(s/def ::block-wrap? boolean?)

(fp/defsc Panel
  [this {::keys [panel-title panel-tag scrollbars? block-wrap?]
         :or    {scrollbars? true
                 block-wrap? true}
         :as    props}]
  {:css [[:.panel
          {:font-size "1rem"}

          ["&:not(:last-child)" {:margin-bottom "1.5rem"}]]

         [:.panel-heading
          {:border-bottom "1px solid #dbdbdb"
           :border-left   "1px solid #dbdbdb"
           :border-right  "1px solid #dbdbdb"}

          {:background-color "#f5f5f5"
           :border-radius    "4px 4px 0 0"
           :color            "#363636"
           :font-size        "1.25em"
           :font-weight      "300"
           :line-height      "1.25"
           :margin           "0"
           :padding          ".5em .75em"}

          {:display     "flex"
           :align-items "center"}

          text-base

          [:&:first-child
           {:border-top "1px solid #dbdbdb"}]]]}
  (dom/div :.panel (dom-props props)
    (dom/div :.panel-heading
      (dom/span (gc :.flex) panel-title)
      (if panel-tag (tag {:classes [:.is-dark]} panel-tag)))
    (if block-wrap?
      (panel-block props
        (if scrollbars?
          (dom/div (gc :.scrollbars)
            (fp/children this))
          (fp/children this)))
      (fp/children this))))

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
                    :padding    "1px 0"}
          text-base]
         [:.arrow {:padding   "0 4px"
                   :font-size "11px"}]]}
  (dom/div :.container (dom-props p)
    (row {:classes (into [:.center] (ccss this :.header))
          :onClick #(on-toggle (not collapsed?))}
      (dom/div :.arrow (if collapsed? "▶" "▼"))
      (dom/div (gc :.flex) title))
    (apply dom/div {:style {:display (if collapsed? "none")}}
      (fp/children this))))

(def collapsible-box (fp/factory CollapsibleBox))

(fp/defsc RawCollapsible
  [this {::keys [collapsed? on-toggle title]
         :or    {on-toggle identity}
         :as    p}]
  {:css [[:.arrow {:cursor    "pointer"
                   :font-size "11px"
                   :padding   "0 4px"}]]}
  (dom/div (dom-props p)
    (row {:classes [:.center]}
      (dom/div :.arrow {:onClick #(on-toggle (not collapsed?))} (if collapsed? "▶" "▼"))
      title)
    (apply dom/div {:style {:display (if collapsed? "none")}}
      (fp/children this))))

(def raw-collapsible (fp/factory RawCollapsible))

(fp/defsc TextField
  [this {:keys  [value]
         ::keys [on-clear left-icon]
         :as    props}]
  ; region css
  {:css [[:.control {:box-sizing "border-box"
                     :clear      "both"
                     :font-size  "1rem"
                     :position   "relative"
                     :text-align "left"}
          ["*" {:box-sizing "inherit"}]

          [:&.has-icons-left :&.has-icons-right
           [:.icon
            {:color          "#dbdbdb"
             :height         "2.25em"
             :pointer-events "none"
             :position       "absolute"
             :top            "0"
             :width          "2.25em"
             :z-index        "4"}]

           [:.input
            [:&.is-small ["&~" [:&.icon {:font-size ".75rem"}]]]]]
          [:&.has-icons-left
           [:.icon [:&.is-left {:left "0"}]]
           [:.input {:padding-left "2.25em"}]]
          [:&.has-icons-right
           [:.icon [:&.is-right {:right "0"}]]
           [:.input {:padding-right "2.25em"}]]]

         [:.icon {:align-items     "center"
                  :display         "inline-flex"
                  :justify-content "center"
                  :height          "1.5rem"
                  :width           "1.5rem"}
          [:&.is-small {:height "1rem"
                        :width  "1rem"}]]

         [:.input
          {:-moz-appearance    "none"
           :-webkit-appearance "none"
           :align-items        "center"
           :border             "1px solid transparent"
           :border-radius      "4px"
           :box-shadow         "none"
           :display            "inline-flex"
           :font-size          "1rem"
           :margin             "0"
           :height             "2.25em"
           :justify-content    "flex-start"
           :line-height        "1.5"
           :padding-bottom     "calc(.375em - 1px)"
           :padding-left       "calc(.625em - 1px)"
           :padding-right      "calc(.625em - 1px)"
           :padding-top        "calc(.375em - 1px)"
           :position           "relative"
           :vertical-align     "top"}

          text-base

          {:background-color "#fff"
           :border-color     "#dbdbdb"
           :color            "#363636"
           :box-shadow       "inset 0 1px 2px rgba(10,10,10,.1)"
           :max-width        "100%"
           :width            "100%"}

          [:&:hover
           {:border-color "#b5b5b5"}]

          [:&:focus
           {:border-color "#3273dc"
            :box-shadow   "0 0 0 0.125em rgba(50,115,220,.25)"
            :outline      "0"}

           ["&~" [:&.icon {:color "#7a7a7a"}]]]

          ["&::placeholder"
           {:color "rgba(54,54,54,.3)"}]

          [:&.is-small {:border-radius "2px"
                        :font-size     ".75rem"}]]

         [:.delete
          {:-moz-appearance    "none"
           :-webkit-appearance "none"
           :background-color   "rgba(10,10,10,.2)"
           :border             "none"
           :border-radius      "290486px"
           :cursor             "pointer"
           :pointer-events     "auto"
           :display            "inline-block"
           :flex-grow          "0"
           :flex-shrink        "0"
           :font-size          "0"
           :height             "20px"
           :max-height         "20px"
           :max-width          "20px"
           :min-height         "20px"
           :min-width          "20px"
           :outline            "0"
           :position           "relative"
           :vertical-align     "top"
           :width              "20px"}
          no-user-select

          ["&::before" "&::after"
           {:background-color         "#fff"
            :content                  "\"\""
            :display                  "block"
            :left                     "50%"
            :position                 "absolute"
            :top                      "50%"
            :-webkit-transform        "translateX(-50%) translateY(-50%) rotate(45deg)"
            :transform                "translateX(-50%) translateY(-50%) rotate(45deg)"
            :-webkit-transform-origin "center center"
            :transform-origin         "center center"}]

          ["&::before"
           {:height "2px"
            :width  "50%"}]

          ["&::after"
           {:height "50%"
            :width  "2px"}]

          [:&.is-small {:height     "16px"
                        :max-height "16px"
                        :max-width  "16px"
                        :min-height "16px"
                        :min-width  "16px"
                        :width      "16px"}]]]}
  ; endregion
  (dom/div
    (dom/div :.control {:classes [(if left-icon :.has-icons-left)
                                  (if on-clear :.has-icons-right)]}
      (dom/input :.input.is-small
        (dom-props
          {:type "text"}
          props))
      (if left-icon
        (dom/span :.icon.is-small.is-left (dom/i {:classes ["fa" left-icon]})))
      (if (and on-clear (seq value))
        (dom/span :.icon.is-small.is-right {:onClick #(on-clear % this)}
          (dom/a :.delete.is-small))))))

(def text-field (fp/factory TextField))

(fp/defsc NumberInput
  [this p]
  {:css            [[:.container {:display     "inline-flex"
                                  :align-items "center"
                                  :font-family "sans-serif"
                                  :font-size   "12px"}]
                    [:.arrow {:cursor      "pointer"
                              :padding     "0px 6px"
                              :user-select "none"}
                     [:&:hover {:background "#b0bec5"}]]
                    [:.input {:border     "0"
                              :outline    "none"
                              :text-align "center"
                              :width      "20px"}
                     ["&::-webkit-inner-spin-button" {:-webkit-appearance "none"}]]]

   :initLocalState (fn [] {:decrease #(let [{:keys [min value onChange]} (fp/props this)]
                                        (onChange (js/Event. "") (cond-> value (> value (or min (- js/Infinity))) dec)))
                           :increase #(let [{:keys [max value onChange]} (fp/props this)]
                                        (onChange (js/Event. "") (cond-> value (< value (or max js/Infinity)) inc)))})}
  (let [p (update p :onChange
            (fn [onChange]
              (if onChange
                (fn [e]
                  (onChange e (-> e event-value gstr/parseInt))))))]
    (dom/div :.container
      (dom/div :.arrow {:onClick (fp/get-state this :decrease)} "<")
      (with-redefs [domc/form-elements? #{}]
        (dom/input :.input (merge {:type "number"} (dom-props p))))
      (dom/div :.arrow {:onClick (fp/get-state this :increase)} ">"))))

(def number-input (fp/factory NumberInput))

(s/def ::active? boolean?)

(fp/defsc ToggleAction
  [this {::keys [active?] :as p}]
  {:css [[:.container {:background  "#f5f5f5"
                       :display     "inline-block"
                       :cursor      "pointer"
                       :user-select "none"
                       :padding     "0 8px"}]
         [:.active {:background "#e0e0e0"}]]}
  (dom/div :.container (dom-props {:classes [(if active? :.active)]} p)
    (fp/children this)))

(def toggle-action (fp/factory ToggleAction))

; endregion

(fp/defsc UIKit [_ _]
  {:css         [[:.flex {:flex "1"}]
                 [:.scrollbars {:overflow "auto"}]
                 [:.no-scrollbars {:overflow "hidden"}]
                 [:.nowrap {:white-space "nowrap"}]]
   :css-include [Button
                 CollapsibleBox
                 Column
                 NumberInput
                 Panel
                 PanelBlock
                 RawCollapsible
                 Row
                 Tag
                 TextField
                 ToggleAction]})

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

(defn component-class [class k]
  (str "." (some-> class css/get-classnames (get-css k))))

(defn ccss [component & k]
  (if-let [css-map (try
                     (some-> component (gobj/get "constructor") css/get-classnames)
                     (catch :default _ nil))]
    (into [] (map (partial get-css css-map)) k)
    []))
