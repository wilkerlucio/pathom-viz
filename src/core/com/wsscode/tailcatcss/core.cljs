(ns com.wsscode.tailcatcss.core
  (:require [garden.core :as garden]
            [garden.stylesheet]))

(def preflight
  [["*" "*::before" "*::after" {:box-sizing "border-box"}]
   [":root" {:-moz-tab-size "4"
             :tab-size      "4"}]
   ["html" {:line-height              "1.15"
            :-webkit-text-size-adjust "100%"}]
   ["body" {:margin "0"}]
   ["body" {:font-family "system-ui, -apple-system, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif, 'Apple Color Emoji', 'Segoe UI Emoji';"}]
   ["hr" {:height "0"
          :color  "inherit"}]
   ["abbr[title]" {:-webkit-text-decoration "underline dotted"
                   :text-decoration         "underline dotted"}]
   ["b" "strong" {:font-weight "bolder"}]
   ["code" "kbd" "samp" "pre" {:font-family "ui-monospace, SFMono-Regular, Consolas, 'Liberation Mono', Menlo, monospace"
                               :font-size   "1em"}]
   ["small" {:font-size "80%"}]
   ["sub" "sup" {:font-size      "75%"
                 :line-height    "0"
                 :position       "relative"
                 :vertical-align "baseline"}]
   ["sub" {:bottom "-0.25em"}]
   ["sup" {:top "-0.5em"}]
   ["table" {:text-indent  "0"
             :border-color "inherit"}]
   ["button" "input" "optgroup" "select" "textarea"
    {:font-family "inherit"
     :font-size   "100%"
     :line-height "1.15"
     :margin      "0"}]
   ["button" "select" {:text-transform "none"}]
   ["button" "[type='button']" "[type='reset']" "[type='submit']"
    {:-webkit-appearance "button"}]
   ["::-moz-focus-inner" {:border-style "none"
                          :padding      "0"}]

   [":-moz-focusring" {:outline "1px dotted ButtonText"}]

   [":-moz-ui-invalid"] {:box-shadow "none"}

   ["legend"] {:padding "0"}

   ["progress"] {:vertical-align "baseline"}

   ["::-webkit-inner-spin-button" "::-webkit-outer-spin-button"
    {:height "auto"}]

   ["[type='search']" {:-webkit-appearance "textfield"
                       :outline-offset     "-2px"}]

   ["::-webkit-search-decoration" {:-webkit-appearance "none"}]

   ["::-webkit-file-upload-button" {:-webkit-appearance "button"
                                    :font               "inherit"}]

   ["summary" {:display "list-item"}]

   ["blockquote" "dl" "dd" "h1" "h2" "h3" "h4" "h5" "h6" "hr" "figure" "p" "pre"
    {:margin "0"}]

   ["button" {:background-color "transparent"
              :background-image "none"}]

   ["button:focus"
    {:outline "1px dotted"}
    {:outline "5px auto -webkit-focus-ring-color"}]

   ["fieldset" {:margin "0" :padding "0"}]

   ["ol" "ul" {:list-style "none"
               :margin     "0"
               :padding    "0"}]

   ["html" {:font-family "ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, \"Helvetica Neue\", Arial, \"Noto Sans\", sans-serif, \"Apple Color Emoji\", \"Segoe UI Emoji\", \"Segoe UI Symbol\", \"Noto Color Emoji\""
            :line-height "1.5"}]

   ["body" {:font-family "inherit"
            :line-height "inherit"}]



   ["*" "*::before" "*::after" {:border-width "0"
                                :border-style "solid"
                                :border-color "#e5e7eb"}]

   ["hr" {:border-top-width "1px"}]

   ["img" {:border-style "solid"}]

   ["textarea" {:resize "vertical"}]

   ["input::placeholder" "textarea::placeholder"
    {:opacity "1" :color "#9ca3af"}]

   ["button" "[role=\"button\"]" {:cursor "pointer"}]

   ["table" {:border-collapse "collapse"}]

   ["h1", "h2", "h3", "h4", "h5", "h6"
    {:font-size   "inherit"
     :font-weight "inherit"}]

   ["a" {:color "inherit" :text-decoration "inherit"}]

   ; Reset form element properties that are easy to forget to
   ; style explicitly so you don't inadvertently introduce
   ; styles that deviate from your design system. These styles
   ; supplement a partial reset that is already applied by
   ; normalize.css.

   ["button" "input" "optgroup" "select" "textarea"
    {:padding     "0"
     :line-height "inherit"
     :color       "inherit"}]

   ; Use the configured 'mono' font family for elements that
   ; are expected to be rendered with a monospace font, falling
   ; back to the system monospace stack if there is no configured
   ; 'mono' font family.

   ["pre" "code" "kbd" "samp"
    {:font-family "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, \"Liberation Mono\", \"Courier New\", monospace"}]

   ["img" "svg" "video" "canvas" "audio" "iframe" "embed" "object"
    {:display        "block"
     :vertical-align "middle"}]

   ; Constrain images and videos to the parent width and preserve
   ; their intrinsic aspect ratio.
   ;
   ; https://github.com/mozdevs/cssremedy/issues/14

   ["img" "video" {:max-width "100%"
                   :height    "auto"}]])

(comment
  (println (garden/css preflight)))

(def colors
  [["transparent" "transparent"]
   ["current" "currentColor"]
   ["black" "rgb(0, 0, 0)"]
   ["white" "rgb(255, 255, 255)"]
   ["gray-50" "rgb(249, 250, 251)"]
   ["gray-100" "rgb(243, 244, 246)"]
   ["gray-200" "rgb(229, 231, 235)"]
   ["gray-300" "rgb(209, 213, 219)"]
   ["gray-400" "rgb(156, 163, 175)"]
   ["gray-500" "rgb(107, 114, 128)"]
   ["gray-600" "rgb(75, 85, 99)"]
   ["gray-700" "rgb(55, 65, 81)"]
   ["gray-800" "rgb(31, 41, 55)"]
   ["gray-900" "rgb(17, 24, 39)"]
   ["red-50" "rgb(254, 242, 242)"]
   ["red-100" "rgb(254, 226, 226)"]
   ["red-200" "rgb(254, 202, 202)"]
   ["red-300" "rgb(252, 165, 165)"]
   ["red-400" "rgb(248, 113, 113)"]
   ["red-500" "rgb(239, 68, 68)"]
   ["red-600" "rgb(220, 38, 38)"]
   ["red-700" "rgb(185, 28, 28)"]
   ["red-800" "rgb(153, 27, 27)"]
   ["red-900" "rgb(127, 29, 29)"]
   ["yellow-50" "rgb(255, 251, 235)"]
   ["yellow-100" "rgb(254, 243, 199)"]
   ["yellow-200" "rgb(253, 230, 138)"]
   ["yellow-300" "rgb(252, 211, 77)"]
   ["yellow-400" "rgb(251, 191, 36)"]
   ["yellow-500" "rgb(245, 158, 11)"]
   ["yellow-600" "rgb(217, 119, 6)"]
   ["yellow-700" "rgb(180, 83, 9)"]
   ["yellow-800" "rgb(146, 64, 14)"]
   ["yellow-900" "rgb(120, 53, 15)"]
   ["green-50" "rgb(236, 253, 245)"]
   ["green-100" "rgb(209, 250, 229)"]
   ["green-200" "rgb(167, 243, 208)"]
   ["green-300" "rgb(110, 231, 183)"]
   ["green-400" "rgb(52, 211, 153)"]
   ["green-500" "rgb(16, 185, 129)"]
   ["green-600" "rgb(5, 150, 105)"]
   ["green-700" "rgb(4, 120, 87)"]
   ["green-800" "rgb(6, 95, 70)"]
   ["green-900" "rgb(6, 78, 59)"]
   ["blue-50" "rgb(239, 246, 255)"]
   ["blue-100" "rgb(219, 234, 254)"]
   ["blue-200" "rgb(191, 219, 254)"]
   ["blue-300" "rgb(147, 197, 253)"]
   ["blue-400" "rgb(96, 165, 250)"]
   ["blue-500" "rgb(59, 130, 246)"]
   ["blue-600" "rgb(37, 99, 235)"]
   ["blue-700" "rgb(29, 78, 216)"]
   ["blue-800" "rgb(30, 64, 175)"]
   ["blue-900" "rgb(30, 58, 138)"]
   ["indigo-50" "rgb(238, 242, 255)"]
   ["indigo-100" "rgb(224, 231, 255)"]
   ["indigo-200" "rgb(199, 210, 254)"]
   ["indigo-300" "rgb(165, 180, 252)"]
   ["indigo-400" "rgb(129, 140, 248)"]
   ["indigo-500" "rgb(99, 102, 241)"]
   ["indigo-600" "rgb(79, 70, 229)"]
   ["indigo-700" "rgb(67, 56, 202)"]
   ["indigo-800" "rgb(55, 48, 163)"]
   ["indigo-900" "rgb(49, 46, 129)"]
   ["purple-50" "rgb(245, 243, 255)"]
   ["purple-100" "rgb(237, 233, 254)"]
   ["purple-200" "rgb(221, 214, 254)"]
   ["purple-300" "rgb(196, 181, 253)"]
   ["purple-400" "rgb(167, 139, 250)"]
   ["purple-500" "rgb(139, 92, 246)"]
   ["purple-600" "rgb(124, 58, 237)"]
   ["purple-700" "rgb(109, 40, 217)"]
   ["purple-800" "rgb(91, 33, 182)"]
   ["purple-900" "rgb(76, 29, 149)"]
   ["pink-50" "rgb(253, 242, 248)"]
   ["pink-100" "rgb(252, 231, 243)"]
   ["pink-200" "rgb(251, 207, 232)"]
   ["pink-300" "rgb(249, 168, 212)"]
   ["pink-400" "rgb(244, 114, 182)"]
   ["pink-500" "rgb(236, 72, 153)"]
   ["pink-600" "rgb(219, 39, 119)"]
   ["pink-700" "rgb(190, 24, 93)"]
   ["pink-800" "rgb(157, 23, 77)"]
   ["pink-900" "rgb(131, 24, 67)"]])

(defn gen-colors [properties prefix]
  (mapv
    (fn [[k v]]
      [(keyword (str "." prefix "-" k))
       (into {}
             (map (fn [p] [p v]))
             properties)])
    colors))

(def text-colors (gen-colors [:color] "text"))
(def background-colors (gen-colors [:background-color] "bg"))
(def border-colors (gen-colors [:border-color] "border"))

(def iteration-table
  ; vector to keep order
  [["0" "0px"]
   ["0.5" "0.125rem"]
   ["1" "0.25rem"]
   ["1.5" "0.375rem"]
   ["2" "0.5rem"]
   ["2.5" "0.625rem"]
   ["3" "0.75rem"]
   ["3.5" "0.875rem"]
   ["4" "1rem"]
   ["5" "1.25rem"]
   ["6" "1.5rem"]
   ["7" "1.75rem"]
   ["8" "2rem"]
   ["9" "2.25rem"]
   ["10" "2.5rem"]
   ["11" "2.75rem"]
   ["12" "3rem"]
   ["14" "3.5rem"]
   ["16" "4rem"]
   ["20" "5rem"]
   ["24" "6rem"]
   ["28" "7rem"]
   ["32" "8rem"]
   ["36" "9rem"]
   ["40" "10rem"]
   ["44" "11rem"]
   ["48" "12rem"]
   ["52" "13rem"]
   ["56" "14rem"]
   ["60" "15rem"]
   ["64" "16rem"]
   ["72" "18rem"]
   ["80" "20rem"]
   ["96" "24rem"]
   ["px" "1px"]])

(defn gen-spaces [properties prefix]
  (mapv
    (fn [[k v]]
      [(keyword (str "." prefix "-" k))
       (into {}
             (map (fn [p] [p v]))
             properties)])
    iteration-table))

(defn gen-spaces+negatives [properties prefix]
  (into
    (mapv
      (fn [[k v]]
        [(keyword (str "." prefix "-" k))
         (into {}
               (map (fn [p] [p v]))
               properties)])
      iteration-table)
    (mapv
      (fn [[k v]]
        [(keyword (str ".-" prefix "-" k))
         (into {}
               (map (fn [p] [p (str "-" v)]))
               properties)])
      iteration-table)))

(def padding (gen-spaces [:padding] "p"))
(def padding-y (gen-spaces [:padding-top :padding-bottom] "py"))
(def padding-x (gen-spaces [:padding-left :padding-right] "px"))
(def padding-top (gen-spaces [:padding-top] "pt"))
(def padding-right (gen-spaces [:padding-right] "pr"))
(def padding-bottom (gen-spaces [:padding-bottom] "pb"))
(def padding-left (gen-spaces [:padding-left] "pl"))

(def all-paddings
  (reduce
    into
    [padding
     padding-y
     padding-x
     padding-top
     padding-right
     padding-bottom
     padding-left]))

(def margin (gen-spaces+negatives [:margin] "m"))
(def margin-y (gen-spaces+negatives [:margin-top :margin-bottom] "my"))
(def margin-x (gen-spaces+negatives [:margin-left :margin-right] "mx"))
(def margin-top (gen-spaces+negatives [:margin-top] "mt"))
(def margin-right (gen-spaces+negatives [:margin-right] "mr"))
(def margin-bottom (gen-spaces+negatives [:margin-bottom] "mb"))
(def margin-left (gen-spaces+negatives [:margin-left] "ml"))

(def all-margins
  (reduce
    into
    [margin
     margin-y
     margin-x
     margin-top
     margin-right
     margin-bottom
     margin-left]))

(def all-spaces (into all-paddings all-margins))

(def borders
  [[:.border-0 {:border-width "0px"}]
   [:.border-2 {:border-width "2px"}]
   [:.border-4 {:border-width "4px"}]
   [:.border-8 {:border-width "8px"}]
   [:.border {:border-width "1px"}]
   [:.border-t-0 {:border-top-width "0px"}]
   [:.border-r-0 {:border-right-width "0px"}]
   [:.border-b-0 {:border-bottom-width "0px"}]
   [:.border-l-0 {:border-left-width "0px"}]
   [:.border-t-2 {:border-top-width "2px"}]
   [:.border-r-2 {:border-right-width "2px"}]
   [:.border-b-2 {:border-bottom-width "2px"}]
   [:.border-l-2 {:border-left-width "2px"}]
   [:.border-t-4 {:border-top-width "4px"}]
   [:.border-r-4 {:border-right-width "4px"}]
   [:.border-b-4 {:border-bottom-width "4px"}]
   [:.border-l-4 {:border-left-width "4px"}]
   [:.border-t-8 {:border-top-width "8px"}]
   [:.border-r-8 {:border-right-width "8px"}]
   [:.border-b-8 {:border-bottom-width "8px"}]
   [:.border-l-8 {:border-left-width "8px"}]
   [:.border-t {:border-top-width "1px"}]
   [:.border-r {:border-right-width "1px"}]
   [:.border-b {:border-bottom-width "1px"}]
   [:.border-l {:border-left-width "1px"}]])

(def overflow
  [[:.overflow-auto {:overflow "auto"}]
   [:.overflow-hidden {:overflow "hidden"}]
   [:.overflow-visible {:overflow "visible"}]
   [:.overflow-scroll {:overflow "scroll"}]
   [:.overflow-x-auto {:overflow-x "auto"}]
   [:.overflow-y-auto {:overflow-y "auto"}]
   [:.overflow-x-hidden {:overflow-x "hidden"}]
   [:.overflow-y-hidden {:overflow-y "hidden"}]
   [:.overflow-x-visible {:overflow-x "visible"}]
   [:.overflow-y-visible {:overflow-y "visible"}]
   [:.overflow-x-scroll {:overflow-x "scroll"}]
   [:.overflow-y-scroll {:overflow-y "scroll"}]])

(def flex
  [[:.flex {:display "flex"}]
   [:.flex-1 {:flex 1}]
   [:.flex-auto {:flex "auto"}]
   [:.flex-initial {:flex "initial"}]
   [:.flex-none {:flex "none"}]
   [:.flex-row {:display        "flex"
                :flex-direction "row"}]
   [:.flex-row-reverse {:display        "flex"
                        :flex-direction "row-reverse"}]
   [:.flex-col {:display        "flex"
                :flex-direction "column"}]
   [:.flex-col-reverse {:display        "flex"
                        :flex-direction "column-reverse"}]

   [:.flex-grow-0 {:flex-grow "0"}]
   [:.flex-grow {:flex-grow "1"}]
   [:.flex-shrink-0 {:flex-shrink "0"}]
   [:.flex-shrink {:flex-shrink "1"}]])

(def align-items
  [[:.items-start {:align-items "flex-start"}]
   [:.items-end {:align-items "flex-end"}]
   [:.items-center {:align-items "center"}]
   [:.items-baseline {:align-items "baseline"}]
   [:.items-stretch {:align-items "stretch"}]])

(def width
  [[".w-0" {:width "0px"}]
   [".w-0.5" {:width "0.125rem"}]
   [".w-1" {:width "0.25rem"}]
   [".w-1.5" {:width "0.375rem"}]
   [".w-2" {:width "0.5rem"}]
   [".w-2.5" {:width "0.625rem"}]
   [".w-3" {:width "0.75rem"}]
   [".w-3.5" {:width "0.875rem"}]
   [".w-4" {:width "1rem"}]
   [".w-5" {:width "1.25rem"}]
   [".w-6" {:width "1.5rem"}]
   [".w-7" {:width "1.75rem"}]
   [".w-8" {:width "2rem"}]
   [".w-9" {:width "2.25rem"}]
   [".w-10" {:width "2.5rem"}]
   [".w-11" {:width "2.75rem"}]
   [".w-12" {:width "3rem"}]
   [".w-14" {:width "3.5rem"}]
   [".w-16" {:width "4rem"}]
   [".w-20" {:width "5rem"}]
   [".w-24" {:width "6rem"}]
   [".w-28" {:width "7rem"}]
   [".w-32" {:width "8rem"}]
   [".w-36" {:width "9rem"}]
   [".w-40" {:width "10rem"}]
   [".w-44" {:width "11rem"}]
   [".w-48" {:width "12rem"}]
   [".w-52" {:width "13rem"}]
   [".w-56" {:width "14rem"}]
   [".w-60" {:width "15rem"}]
   [".w-64" {:width "16rem"}]
   [".w-72" {:width "18rem"}]
   [".w-80" {:width "20rem"}]
   [".w-96" {:width "24rem"}]
   [".w-auto" {:width "auto"}]
   [".w-px" {:width "1px"}]
   [".w-1/2" {:width "50%"}]
   [".w-1/3" {:width "33.333333%"}]
   [".w-2/3" {:width "66.666667%"}]
   [".w-1/4" {:width "25%"}]
   [".w-2/4" {:width "50%"}]
   [".w-3/4" {:width "75%"}]
   [".w-1/5" {:width "20%"}]
   [".w-2/5" {:width "40%"}]
   [".w-3/5" {:width "60%"}]
   [".w-4/5" {:width "80%"}]
   [".w-1/6" {:width "16.666667%"}]
   [".w-2/6" {:width "33.333333%"}]
   [".w-3/6" {:width "50%"}]
   [".w-4/6" {:width "66.666667%"}]
   [".w-5/6" {:width "83.333333%"}]
   [".w-1/12" {:width "8.333333%"}]
   [".w-2/12" {:width "16.666667%"}]
   [".w-3/12" {:width "25%"}]
   [".w-4/12" {:width "33.333333%"}]
   [".w-5/12" {:width "41.666667%"}]
   [".w-6/12" {:width "50%"}]
   [".w-7/12" {:width "58.333333%"}]
   [".w-8/12" {:width "66.666667%"}]
   [".w-9/12" {:width "75%"}]
   [".w-10/12" {:width "83.333333%"}]
   [".w-11/12" {:width "91.666667%"}]
   [".w-full" {:width "100%"}]
   [".w-screen" {:width "100vw"}]
   [".w-min" {:width "min-content"}]
   [".w-max" {:width "max-content"}]])

(def max-width
  [[:.max-w-0 {:max-width "0rem"}]
   [:.max-w-none {:max-width "none"}]
   [:.max-w-xs {:max-width "20rem"}]
   [:.max-w-sm {:max-width "24rem"}]
   [:.max-w-md {:max-width "28rem"}]
   [:.max-w-lg {:max-width "32rem"}]
   [:.max-w-xl {:max-width "36rem"}]
   [:.max-w-2xl {:max-width "42rem"}]
   [:.max-w-3xl {:max-width "48rem"}]
   [:.max-w-4xl {:max-width "56rem"}]
   [:.max-w-5xl {:max-width "64rem"}]
   [:.max-w-6xl {:max-width "72rem"}]
   [:.max-w-7xl {:max-width "80rem"}]
   [:.max-w-full {:max-width "100%"}]
   [:.max-w-min {:max-width "min-content"}]
   [:.max-w-max {:max-width "max-content"}]
   [:.max-w-prose {:max-width "65ch"}]
   [:.max-w-screen-sm {:max-width "640px"}]
   [:.max-w-screen-md {:max-width "768px"}]
   [:.max-w-screen-lg {:max-width "1024px"}]
   [:.max-w-screen-xl {:max-width "1280px"}]
   [:.max-w-screen-2xl {:max-width "1536px"}]])

(def font-family
  [[:.font-sans {:font-family "ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, \"Helvetica Neue\", Arial, \"Noto Sans\", sans-serif, \"Apple Color Emoji\", \"Segoe UI Emoji\", \"Segoe UI Symbol\", \"Noto Color Emoji\""}]
   [:.font-serif {:font-family "ui-serif, Georgia, Cambria, \"Times New Roman\", Times, serif"}]
   [:.font-mono {:font-family "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, \"Liberation Mono\", \"Courier New\", monospace"}]])

(def font-size
  [[:.text-xs {:font-size   "0.75rem"
               :line-height "1rem"}]
   [:.text-sm {:font-size   "0.875rem"
               :line-height "1.25rem"}]
   [:.text-base {:font-size   "1rem"
                 :line-height "1.5rem"}]
   [:.text-lg {:font-size   "1.125rem"
               :line-height "1.75rem"}]
   [:.text-xl {:font-size   "1.25rem"
               :line-height "1.75rem"}]
   [:.text-2xl {:font-size   "1.5rem"
                :line-height "2rem"}]
   [:.text-3xl {:font-size   "1.875rem"
                :line-height "2.25rem"}]
   [:.text-4xl {:font-size   "2.25rem"
                :line-height "2.5rem"}]
   [:.text-5xl {:font-size   "3rem"
                :line-height "1"}]
   [:.text-6xl {:font-size   "3.75rem"
                :line-height "1"}]
   [:.text-7xl {:font-size   "4.5rem"
                :line-height "1"}]
   [:.text-8xl {:font-size   "6rem"
                :line-height "1"}]
   [:.text-9xl {:font-size   "8rem"
                :line-height "1"}]])

(def bases
  (reduce
    into
    [text-colors
     background-colors
     border-colors
     all-spaces
     borders
     overflow
     flex
     align-items
     max-width
     width
     font-family
     font-size]))

(defn prefix-classname [x prefix]
  (str "." prefix (subs (name x) 1)))

(defn responsive-selectors [min-width prefix rules]
  (garden.stylesheet/at-media {:min-width min-width}
    (into [] (map #(update % 0 prefix-classname (str prefix ":"))) rules)))

(def everything
  (conj
    (into [preflight] bases)
    (responsive-selectors "640px" "sm" bases)
    (responsive-selectors "768px" "md" bases)
    (responsive-selectors "1024px" "lg" bases)
    (responsive-selectors "1536px" "2xl" bases)))

(defn compute-css []
  (garden/css everything))
