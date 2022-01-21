(ns com.wsscode.pathom.viz.ui.kit
  (:require ["react-draggable" :refer [DraggableCore]]
            [cljs.spec.alpha :as s]
            [com.fulcrologic.fulcro-css.css :as css]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.fulcrologic.fulcro.components :as fc]
            [com.fulcrologic.fulcro.dom :as domc]
            [com.fulcrologic.fulcro.mutations :as fm]
            [com.fulcrologic.guardrails.core :refer [>def >defn >fdef => | <- ?]]
            [com.wsscode.pathom.misc :as p.misc]
            [com.wsscode.pathom.viz.helpers :as h]
            [helix.core :as hx]
            [goog.object :as gobj]
            [goog.string :as gstr]
            [garden.selectors :as gs]
            [clojure.set :as set]))

(declare gc css ccss)

; region variables

(def font-base "BlinkMacSystemFont,-apple-system,\"Segoe UI\",Roboto,Oxygen,Ubuntu,Cantarell,\"Fira Sans\",\"Droid Sans\",\"Helvetica Neue\",Helvetica,Arial,sans-serif")
(def font-code "monospace!important")

(def text-base {:font-family font-base
                :line-height "1.5"})

(def text-sans-13
  {:font-family "sans-serif"
   :font-size   "13px"})

(def text-sans-13'
  ["font-sans" "text-sm"])

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

(def color-highlight "#9fdcff")

; endregion

; region helpers

(defn normalize-props [props classes]
  (update props :classes #(into classes %)))

(defn styled-component [component classes]
  (fn styled-component-internal
    ([props]
     (component (normalize-props props classes)))
    ([props child]
     (component (normalize-props props classes) child))
    ([props c1 c2]
     (component (normalize-props props classes) c1 c2))
    ([props c1 c2 c3]
     (component (normalize-props props classes) c1 c2 c3))
    ([props c1 c2 c3 c4]
     (component (normalize-props props classes) c1 c2 c3 c4))
    ([props c1 c2 c3 c4 & children]
     (apply component (normalize-props props classes) c1 c2 c3 c4 children))))

(defn add-class [props class]
  (update props :classes p.misc/vconj class))

(def mergers
  {:classes (fn [a b]
              (into a b))
   :class   (fn [a b] (str a " " b))})

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

(def button
  (styled-component domc/button
    ["text-xs text-white font-semibold"
     "bg-gray-700 rounded px-2 py-1"
     ; hover
     "hover:bg-gray-500"
     "focus:outline-none focus:ring"
     "disabled:opacity-50 disabled:cursor-not-allowed"]))

#_
(defn button [props & children]
  (apply dom/button (dom-props {:classes [""]} props) children))

(fc/defsc Column
  [this props]
  {:css [[:.container {:display        "flex"
                       :flex-direction "column"
                       :max-width      "100%"
                       :height         "100%"
                       :overflow       "hidden"}]
         [(gs/> :.container "*") {:min-height "10px"}]]}
  (dom/div :.container props
    (fc/children this)))

(def column (fc/factory Column))

(fc/defsc Row
  [this props]
  {:css [[:.container {:display   "flex"
                       :max-width "100%"
                       :overflow  "hidden"}]
         [(gs/> :.container "*") {:min-width "10px"}]
         #_[:* {:min-width "10px"}]
         [:.center {:align-items "center"}]
         [:.stretch {:align-items "stretch"}]]}
  (dom/div :.container props (fc/children this)))

(def row (fc/factory Row))

(def link
  (styled-component domc/a
    ["text-blue-600"
     "hover:underline"]))

(fc/defsc ErrorBoundary
  [this _]
  {:componentDidCatch
   (fn [_this error info]
     (js/console.error "Error boundary error" error info))

   :getDerivedStateFromError
   (fn [_error]
     {::error-catch? true})}

  (if (fc/get-state this ::error-catch?)
    (dom/div {:className "flex-1 flex-row items-center justify-center bg-red-300 text-white"}
      "Something went wrong :'(")
    (fc/children this)))

(def error-boundary (fc/factory ErrorBoundary))

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
  {:css [[:.container {:cursor   "pointer"
                       :overflow "hidden"}]
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

(fc/defsc SectionHeader
  [this props]
  {:css [[:.container {:background    "#f7f7f7"
                       :border-bottom "1px solid #ddd"
                       :padding       "4px 8px"}
          text-sans-13]]}
  (dom/div :.container (dom-props props) (fc/children this)))

(defn section-header [props & children]
  (apply dom/div
    (dom-props {:className "py-1 px-2 border-b border-gray-300 bg-gray-100 font-sans text-sm"}
      props)
    children))

#_ (def section-header (fc/factory SectionHeader))

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

(fc/defsc Modal
  [this {::keys []}]
  {:css        [[:.outer-container
                 {:background      "rgba(0,0,0,0.6)"
                  :position        "fixed"
                  :left            "0"
                  :top             "0"
                  :right           "0"
                  :bottom          "0"
                  :display         "flex"
                  :align-items     "center"
                  :justify-content "center"
                  :z-index         "10"}]
                [:.container {:background "#fff"
                              :border     "1px solid #ddd"
                              :padding    "10px"}]]
   :use-hooks? true}
  (dom/div :.outer-container
    (dom/div :.container
      (fc/children this))))

(def modal (fc/factory Modal {}))

(defn input [{:keys [state] :as props}]
  (let [props (dissoc props :state)]
    (dom/input :$border$rounded$w-full (assoc props :value @state :onChange #(reset! state (event-value %))))))

(fc/defsc PromptModal
  [_this {:keys [prompt value on-finish]}]
  {:use-hooks? true}
  (let [text             (h/use-atom-state (or value ""))
        headers          (h/use-atom-state {})
        new-header-key   (h/use-atom-state "")
        new-header-value (h/use-atom-state "")
        add-header!      (fn []
                           (swap! headers assoc @new-header-key @new-header-value)
                           (reset! new-header-key "")
                           (reset! new-header-value ""))
        remove-header!   (fn [k]
                           (swap! headers dissoc k))]
    (modal {}
      (dom/div {:classes ["space-y-2"]}
        (dom/div (str prompt))
        (dom/div (input {:state text :autoFocus true}))
        (dom/div "Headers")
        (dom/div {:classes ["flex" "items-center" "space-x-2"]}
          (input {:state new-header-key :placeholder "Header Key"})
          (input {:state new-header-value :placeholder "Header Value"})
          (button {:onClick add-header!} "+"))
        (dom/div
          (for [[k v] (sort-by key @headers)]
            (dom/div {:classes ["flex" "space-x-2" "text-sm"] :key k}
              (dom/div {:classes ["text-gray-700" "font-bold"]} (str k ":"))
              (dom/div {:classes ["max-w-[50vw]" "text-ellipsis" "overflow-hidden" "whitespace-nowrap"]} v)
              (button {:onClick #(remove-header! k)} "-"))))
        (dom/div {:classes ["space-x-1"]}
          (button {:onClick #(on-finish {:url @text :headers @headers})} "Ok")
          (button {:onClick #(on-finish nil)} "Cancel"))))))

(def prompt-modal (fc/factory PromptModal))

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
                       :max-height     "100%"
                       :overflow       "hidden"}]]}
  (dom/div :.container props (fc/children this)))

(def tab-container (fc/factory TabContainer))

(>def ::active-tab-id any?)
(>def ::tab-id any?)
(>def ::on-tab-close fn?)

(fc/defsc TabNav
  [this {::keys [active-tab-id target tab-right-tools] :as props}]
  {:css [[:.container {:align-items   "baseline"
                       :background    "#eee"
                       :border        "1px solid #ddd"
                       :display       "flex"
                       :margin-bottom "-1px"
                       :padding-right "4px"}
          [:&.border-collapse-bottom {:border-bottom "none"}]]
         [:.tab {:align-items   "center"
                 :border-bottom "2px solid transparent"
                 :cursor        "pointer"
                 :display       "flex"
                 :padding       "5px 9px"}
          text-sans-13
          [:&:hover {:background "#e5e5e5"}]]
         [:.tab-active {:border-bottom "2px solid #5c7ebb"
                        :z-index       "1"}]
         [:.x {:align-self    "flex-start"
               :border-radius "50%"
               :margin-left   "6px"
               :font-family   "monospace"
               :font-size     "10px"
               :padding       "1px 4px"}
          [:&:hover {:background "#aaa"}]]]}
  (dom/div :.container props
    (for [[{::keys [tab-id on-tab-close] :as p} & c] (fc/children this)
          :let [active? (= tab-id active-tab-id)]]
      (dom/div :.tab
        (cond-> (-> p
                    h/keep-unamespaced
                    (assoc :key (pr-str tab-id)))
          target (assoc :onClick #(fm/set-value! target ::active-tab-id tab-id))
          active? (add-class :.tab-active))
        (if on-tab-close
          (fc/fragment
            c
            (dom/div :.x {:onClick (stop-propagation on-tab-close)} "✕"))
          c)))
    (if tab-right-tools
      (fc/fragment
        (dom/div (gc :.flex))
        tab-right-tools))))

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

(def native-select
  (styled-component domc/select
    ["block pl-2 pr-6 py-0.5 text-sm border-gray-300 rounded-md"
     "focus:outline-none focus:border-gray-500 bg-none"]))

(defn dom-select
  "Similar to fulcro dom/select, but does value encode/decode in EDN so you can use
  EDN values directly."
  [props & children]
  (apply native-select
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

(>def ::direction #{"up" "down" "left" "right"})

(hx/defnc DragResizeHelix [{:keys [state direction props react-key kids]}]
  (let [start      (h/use-atom-state nil)
        start-size (h/use-atom-state nil)
        axis       (get {"left"  "x"
                         "right" "x"
                         "up"    "y"
                         "down"  "y"} direction "x")
        invert?    (get {"left"  true
                         "right" false
                         "up"    true
                         "down"  false} direction true)
        css        (if (= "x" axis) {:cursor        "ew-resize"
                                     :width         "20px"
                                     :background    "#eee"
                                     :border        "1px solid #e0e0e0"
                                     :borderTop     "0"
                                     :borderBottom  "0"
                                     :pointerEvents "all"
                                     :zIndex        "2"}
                                    {:cursor        "ns-resize"
                                     :minHeight     "20px"
                                     :background    "#eee"
                                     :border        "1px solid #e0e0e0"
                                     :borderLeft    "0"
                                     :borderRight   "0"
                                     :pointerEvents "all"
                                     :padding       "4px 8px"
                                     :zIndex        "2"})]
    (js/React.createElement DraggableCore
      #js {:key     (or react-key "dragHandler")
           :onStart (fn [_e dd]
                      (reset! start (gobj/get dd axis))
                      (reset! start-size @state))
           :onDrag  (fn [_e dd]
                      (let [start    @start
                            size     @start-size
                            value    (gobj/get dd axis)
                            new-size (+ size (if invert? (- value start) (- start value)))]
                        (reset! state new-size)))}
      (apply dom/div (merge {:classes   (into text-sans-13' ["flex-shrink-0"])
                             :style     css} props) kids))))

(defn drag-resize
  "Creates a visual component that can be dragged to control the size of another component.

  The :state prop should be an `atom-like` state and will reflect the current size.

  The :direction can be up, down, left or right, think about it as what is the position
  of the element being resized, relative to the drag handler (which is this component).

  Row example:
    -------------------------
    | DIV1 | HANDLER | DIV2 |
    -------------------------

  In the row setting in the example before, if you want the size to apply into DIV1, use
  the \"left\" direction. For DIV2 use \"right\".

  Column example:
    -----------
    | DIV1    |
    -----------
    | HANDLER |
    -----------
    | DIV2    |
    -----------

  In this column example, use \"up\" if you want to control the DIV1, and \"down\" if
  you want to control the size of DIV2.
  "
  [props & children]
  (let [props (set/rename-keys props {:key :react-key})]
    (hx/$ DragResizeHelix {:kids children :& props})))

; endregion

(fc/defsc UIKit [_ _]
  {:css         [[:$CodeMirror {:height   "100% !important"
                                :width    "100% !important"
                                :position "absolute !important"
                                :z-index  "1"}
                  [:$cm-atom-composite {:color "#ab890d"}]
                  [:$cm-atom-ident {:color       "#219"
                                    :font-weight "bold"}]]
                 [:$CodeMirror-hint {:font-size "10px"}]
                 [:.flex {:flex "1"}]
                 [:.center {:text-align "center"}]
                 [:.scrollbars {:overflow "auto"}]
                 [:.no-scrollbars {:overflow "hidden"}]
                 [:.nowrap {:white-space "nowrap"}]
                 [:.height-100 {:height "100%"}]
                 [:.max-width-100 {:max-width "100%"}]
                 [:.border-collapse-top {:border-top "none !important"}]
                 [:.border-collapse-right {:border-right "none !important"}]
                 [:.border-collapse-bottom {:border-bottom "none !important"}]
                 [:.border-collapse-left {:border-left "none !important"}]
                 []
                 [:.divisor-v {:cursor         "ew-resize"
                               :width          "20px"
                               :background     "#eee"
                               :border         "1px solid #e0e0e0"
                               :border-top     "0"
                               :border-bottom  "0"
                               :pointer-events "all"
                               :z-index        "2"}]
                 [:.divisor-h {:cursor         "ns-resize"
                               :height         "20px"
                               :background     "#eee"
                               :border         "1px solid #e0e0e0"
                               :border-left    "0"
                               :border-right   "0"
                               :pointer-events "all"
                               :padding        "4px 8px"
                               :z-index        "2"}
                  text-sans-13]]
   :css-include [CollapsibleBox
                 Column
                 Modal
                 NumberInput
                 Panel
                 PanelBlock
                 RawCollapsible
                 Row
                 SectionHeader
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
  (some-> class css/get-classnames (get-css k)))

(defn ccss [component & k]
  (if-let [css-map (try
                     (some-> component fc/react-type css/get-classnames)
                     (catch :default _ nil))]
    (into [] (map (partial get-css css-map)) k)
    []))
