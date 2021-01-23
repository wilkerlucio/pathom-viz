(ns com.wsscode.pathom.viz.trace
  (:require ["./d3-trace" :refer [renderPathomTrace updateTraceSize]]
            ["./detect-element-size" :refer [addResizeListener]]
            [clojure.walk :as walk]
            [com.wsscode.pathom.viz.helpers :as h]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.fulcrologic.fulcro.mutations :as fm]
            [com.fulcrologic.fulcro.components :as fc]
            [goog.object :as gobj]))

(defn add-edn-dna [trace]
  (walk/postwalk
    (fn [x]
      (if (and (map? x) (contains? x :event))
        (assoc x :edn-original (volatile! x))
        x))
    trace))

(defn render-trace [this]
  (let [{::keys [trace-data on-show-details]} (-> this fc/props)
        container (gobj/get this "svgContainer")
        svg (gobj/get this "svg")]
    (gobj/set svg "innerHTML" "")
    (gobj/set this "renderedData"
      (renderPathomTrace svg
        (clj->js {:svgWidth    (gobj/get container "clientWidth")
                  :svgHeight   (gobj/get container "clientHeight")
                  :data        (-> trace-data add-edn-dna (h/stringify-keyword-values))
                  :showDetails (or on-show-details identity)})))))

(defn recompute-trace-size [this]
  (let [container (gobj/get this "svgContainer")]
    (updateTraceSize
      (doto (gobj/get this "renderedData")
        (gobj/set "svgWidth" (gobj/get container "clientWidth"))
        (gobj/set "svgHeight" (gobj/get container "clientHeight"))))))

(fc/defsc D3Trace [this _]
  {:css
   [[:.container {:flex      1
                  :max-width "100%"
                  :height    "100%"
                  :overflow  "hidden"}]

    [:$pathom-attribute
     {:fill    "#d4d4d4"
      :opacity "0.5"}

     [:&:hover
      {:fill "#94a0ad"}]]

    [:$pathom-attribute-bounds
     {:fill             "none"
      :opacity          "0.5"
      :stroke           "#000"
      :stroke-dasharray "5 1"
      :visibility       "hidden"}]

    [:$pathom-detail-marker
     {:fill    "#a4e3bf"
      :opacity "0.9"}
     [:&:hover
      {:opacity "1"}]]

    [:$pathom-event-waiting-resolver
     {:fill "#de5615"}]

    [:$pathom-event-skip-wait-key
     {:fill "#de5615"}]

    [:$pathom-event-external-wait-key
     {:fill "#de5615"}]

    [:$pathom-event-call-resolver
     {:fill "#af9df4"}]

    [:$pathom-event-call-resolver-batch
     {:fill "rgba(42, 0, 208, 0.5)"}]

    [:$pathom-event-schedule-resolver
     {:fill "#efaf42"}]

    [:$pathom-event-error
     {:fill "#bb0808"}]

    [:$pathom-label-text
     {:font-family    "sans-serif"
      :fill           "#222"
      :font-size      "11px"
      :pointer-events "none"}]

    [:$pathom-vruler
     {:stroke       "#2b98f0"
      :stroke-width "2px"
      :visibility   "hidden"}]

    [:$pathom-axis
     [:line
      {:stroke "#e5e5e5"}]]

    [:$pathom-tooltip
     {:position       "fixed"
      :pointer-events "none"
      :font-size      "12px"
      :font-family    "sans-serif"
      :background     "#fff"
      :padding        "1px 6px"
      :word-break     "break-all"
      :box-shadow     "#00000069 0px 1px 3px 0px"
      :white-space    "nowrap"
      :top            "-1000px"
      :left           "-1000px"
      :z-index        "10"}]

    [:$pathom-label-text-fade
     {:opacity "0.5"}]

    [:$pathom-details-count
     {:background    "#8bdc47"
      :border-radius "7px"
      :padding       "1px 5px"
      :font-size     "10px"
      :font-family   "sans-serif"}]

    [:$pathom-children-count
     {:background    "#d2a753"
      :border-radius "7px"
      :padding       "1px 5px"
      :font-size     "10px"
      :font-family   "sans-serif"}]

    [:$pathom-attribute-toggle-children
     {:cursor      "default"
      :fill        "#757575"
      :font-size   "16px"
      :font-family "monospace"
      :font-weight "bold"
      :text-anchor "middle"
      :transform   "translate(-8px, 13px)"}]]

   :componentDidMount
   (fn [this]
     (render-trace this)
     (addResizeListener (gobj/get this "svgContainer") #(recompute-trace-size this)))

   :componentDidUpdate
   (fn [this prev-props _]
     (if (= (-> prev-props ::trace-data)
            (-> this fc/props ::trace-data))
       (recompute-trace-size this)
       (render-trace this)))

   :componentDidCatch
   (fn [this error info]
     (fc/set-state! this {::error-catch? true}))}

  (dom/div :.container {:ref #(gobj/set this "svgContainer" %)}
    (if (fc/get-state this ::error-catch?)
      (dom/div "Error rendering trace, check console for details")
      (dom/svg {:ref #(gobj/set this "svg" %)}))))

(def d3-trace (fc/factory D3Trace))
