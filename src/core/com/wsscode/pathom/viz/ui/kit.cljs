(ns com.wsscode.pathom.viz.ui.kit
  (:require [cljs.spec.alpha :as s]
            [com.fulcrologic.fulcro-css.css :as css]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.fulcrologic.fulcro.components :as fc]
            [com.fulcrologic.fulcro.dom :as domc]
            [com.fulcrologic.fulcro.mutations :as fm]
            [com.fulcrologic.guardrails.core :refer [>def >defn >fdef => | <- ?]]
            [com.wsscode.pathom.misc :as p.misc]
            [com.wsscode.pathom.viz.helpers :as h]
            [goog.object :as gobj]
            [goog.string :as gstr]))

(declare gc css ccss)

; region variables

(def font-base "BlinkMacSystemFont,-apple-system,\"Segoe UI\",Roboto,Oxygen,Ubuntu,Cantarell,\"Fira Sans\",\"Droid Sans\",\"Helvetica Neue\",Helvetica,Arial,sans-serif")
(def font-code "monospace!important")

(def text-base {:font-family font-base
                :line-height "1.5"})

(def text-sans-13
  {:font-family "sans-serif"
   :font-size   "13px"})

(def css-header
  {:margin      "0"
   :font-size   "2rem"
   :font-weight "600"})

(def no-user-select
  {:-webkit-touch-callout "none"
   :-webkit-user-select   "none"
   :-moz-user-select      "none"
   :-ms-user-select       "none"
   :user-select           "none"})

; endregion

; region helpers

(defn add-class [props class]
  (update props :classes p.misc/vconj class))

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

(defn prevent-default
  "Wrap a callback function f to prevent default event behavior."
  [f]
  (fn [e]
    (.preventDefault e)
    (f e)))

(defn stop-propagation
  "Wrap a callback function f to prevent default event behavior."
  [f]
  (fn [e]
    (.stopPropagation e)
    (f e)))

; endregion

; region basics

(fc/defsc Button
  [this props]
  {:css [[:.button {:cursor  "pointer"
                    :padding "1px 7px 2px"}
          [:&:disabled {:cursor "not-allowed"}]]]}
  (dom/button :.button props (fc/children this)))

(def button (fc/factory Button))

(fc/defsc Column
  [this props]
  {:css [[:.container {:display        "flex"
                       :flex-direction "column"
                       :max-width      "100%"
                       :max-height     "100%"}]]}
  (dom/div :.container props
    (fc/children this)))

(def column (fc/factory Column))

(fc/defsc Row
  [this props]
  {:css [[:.container {:display   "flex"
                       :max-width "100%"}]
         [:.center {:align-items "center"}]
         [:.stretch {:align-items "center"}]]}
  (dom/div :.container props (fc/children this)))

(def row (fc/factory Row))

; endregion

; region components

(fc/defsc Tag
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
            :color            "#fff"}]
          [:&.is-dark
           {:background-color "#363636"
            :color            "#f5f5f5"}]]]}
  (dom/span :.tag (dom-props props) (fc/children this)))

(def tag (fc/factory Tag))

(fc/defsc PanelBlock
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
        (fc/children this))
      (fc/children this))))

(def panel-block (fc/factory PanelBlock))

(s/def ::panel-title string?)
(s/def ::panel-tag (s/or :string string? :number number?))
(s/def ::scrollbars? boolean?)
(s/def ::block-wrap? boolean?)

(fc/defsc Panel
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
            (fc/children this))
          (fc/children this)))
      (fc/children this))))

(def panel (fc/factory Panel))

(s/def ::title string?)
(s/def ::collapsed? boolean?)
(s/def ::on-toggle (s/fspec :args (s/cat :active? boolean?)))

(fc/defsc CollapsibleBox
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
      (fc/children this))))

(def collapsible-box (fc/factory CollapsibleBox))

(fc/defsc RawCollapsible
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
      (fc/children this))))

(def raw-collapsible (fc/factory RawCollapsible))

(fc/defsc TextField
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

(def text-field (fc/factory TextField))

(fc/defsc TabContainer
  [this props]
  {:css [[:.container {:display        "flex"
                       :flex           "1"
                       :flex-direction "column"
                       :max-width      "100%"
                       :max-height     "100%"}]]}
  (dom/div :.container props (fc/children this)))

(def tab-container (fc/factory TabContainer))

(>def ::active-tab-id any?)
(>def ::tab-id any?)
(>def ::on-tab-close fn?)

(fc/defsc TabNav
  [this {::keys [active-tab-id target] :as props}]
  {:css [[:.container {:background    "#eee"
                       :border        "1px solid #ddd"
                       :display       "flex"
                       :margin-bottom "-1px"}
          [:&.border-collapse-bottom {:border-bottom "none"}]]
         [:.tab {:cursor  "pointer"
                 :display "flex"
                 :padding "5px 9px"}
          text-sans-13
          [:&:hover {:background "#e5e5e5"}]]
         [:.tab-active {:border-bottom "2px solid #5c7ebb"
                        :z-index       "1"}]]}
  (dom/div :.container props
    (for [[{::keys [tab-id on-tab-close] :as p} & c] (fc/children this)
          :let [active? (= tab-id active-tab-id)]]
      (dom/div :.tab
        (cond-> (assoc p :key (pr-str tab-id))
          target (assoc :onClick #(fm/set-value! target ::active-tab-id tab-id))
          active? (add-class :.tab-active))
        (if on-tab-close
          (fc/fragment
            c
            (dom/div :.x {:onClick (stop-propagation on-tab-close)} "X"))
          c)))))

(def tab-nav (fc/factory TabNav))

(fc/defsc NumberInput
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

   :initLocalState (fn [this]
                     {:decrease #(let [{:keys [min value onChange]} (fc/props this)]
                                   (onChange (js/Event. "") (cond-> value (> value (or min (- js/Infinity))) dec)))
                      :increase #(let [{:keys [max value onChange]} (fc/props this)]
                                   (onChange (js/Event. "") (cond-> value (< value (or max js/Infinity)) inc)))})}
  (let [p (update p :onChange
            (fn [onChange]
              (if onChange
                (fn [e]
                  (onChange e (-> e event-value gstr/parseInt))))))]
    (dom/div :.container
      (dom/div :.arrow {:onClick (fc/get-state this :decrease)} "<")
      (with-redefs [domc/form-elements? #{}]
        (dom/input :.input (merge {:type "number"} (dom-props p))))
      (dom/div :.arrow {:onClick (fc/get-state this :increase)} ">"))))

(def number-input (fc/factory NumberInput))

(defn dom-select
  "Similar to fulcro dom/select, but does value encode/decode in EDN so you can use
  EDN values directly."
  [props & children]
  (apply dom/select
    (-> props
        (update :value pr-str)
        (update :onChange (fn [f]
                            (fn [e]
                              (f e (h/safe-read (.. e -target -value)))))))
    children))

(defn dom-option
  "Similar to fulcro dom/option, but does value encode/decode in EDN so you can use
  EDN values directly."
  [props & children]
  (apply dom/option
    (-> props
        (update :value pr-str))
    children))

(s/def ::active? boolean?)

(fc/defsc ToggleAction
  [this {::keys [active?] :as p}]
  {:css [[:.container {:background  "#f5f5f5"
                       :display     "inline-block"
                       :cursor      "pointer"
                       :user-select "none"
                       :padding     "0 8px"}]
         [:.active {:background "#e0e0e0"}]]}
  (dom/div :.container (dom-props {:classes [(if active? :.active)]} p)
    (fc/children this)))

(def toggle-action (fc/factory ToggleAction))

(fc/defsc Toolbar
  [this props]
  {:css [[:.container {:background    "#eeeeee"
                       :border-bottom "1px solid #e0e0e0"
                       :padding       "5px 4px"
                       :display       "flex"
                       :align-items   "center"
                       :font-family   "sans-serif"
                       :font-size     "13px"}
          [:label {:display     "flex"
                   :align-items "center"}
           [:input {:margin-right "5px"}]]]]}
  (apply dom/div :.container props (fc/children this)))

(def toolbar (fc/factory Toolbar))

; endregion

(fc/defsc UIKit [_ _]
  {:css         [[:.flex {:flex "1"}]
                 [:.scrollbars {:overflow "auto"}]
                 [:.no-scrollbars {:overflow "hidden"}]
                 [:.nowrap {:white-space "nowrap"}]
                 [:.height-100 {:height "100%"}]
                 [:.max-width-100 {:max-width "100%"}]
                 [:.divisor-v {:width         "20px"
                               :background    "#eee"
                               :border        "1px solid #e0e0e0"
                               :border-top    "0"
                               :border-bottom "0"
                               :z-index       "2"}]
                 [:.divisor-h {:height       "20px"
                               :background   "#eee"
                               :border       "1px solid #e0e0e0"
                               :border-left  "0"
                               :border-right "0"
                               :z-index      "2"}]]
   :css-include [Button
                 CollapsibleBox
                 Column
                 NumberInput
                 Panel
                 PanelBlock
                 RawCollapsible
                 Row
                 TabContainer
                 TabNav
                 Tag
                 TextField
                 ToggleAction
                 Toolbar]})

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
  Eg: (gc :.flex :.scrollbars)"
  [& k]
  {:classes (mapv css k)})

(defn component-class [class k]
  (str "." (some-> class css/get-classnames (get-css k))))

(defn ccss [component & k]
  (if-let [css-map (try
                     (some-> component fc/react-type css/get-classnames)
                     (catch :default _ nil))]
    (into [] (map (partial get-css css-map)) k)
    []))
