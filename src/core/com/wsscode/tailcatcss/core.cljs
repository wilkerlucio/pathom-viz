(ns com.wsscode.tailcatcss.core
  (:require [garden.core :as garden]))

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

(def margin (gen-spaces [:margin] "p"))
(def margin-y (gen-spaces [:margin-top :margin-bottom] "py"))
(def margin-x (gen-spaces [:margin-left :margin-right] "px"))
(def margin-top (gen-spaces [:margin-top] "pt"))
(def margin-right (gen-spaces [:margin-right] "pr"))
(def margin-bottom (gen-spaces [:margin-bottom] "pb"))
(def margin-left (gen-spaces [:margin-left] "pl"))

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
   [:.flex-row {:flex-direction "row"}]
   [:.flex-row-reverse {:flex-direction "row-reverse"}]
   [:.flex-col {:flex-direction "column"}]
   [:.flex-col-reverse {:flex-direction "column-reverse"}]])

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

(def everything
  (reduce
    into
    [text-colors
     background-colors
     all-spaces
     overflow
     flex
     max-width]))

(defn compute-css []
  (garden/css everything))
