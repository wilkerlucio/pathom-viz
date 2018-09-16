(ns com.wsscode.pathom.viz.trace
  (:require ["./d3-trace" :refer [renderPathomTrace updateTraceSize]]
            ["./detect-element-size"]
            [fulcro.client.localized-dom :as dom]
            [fulcro.client.primitives :as fp]
            [goog.object :as gobj]
            [fulcro.client.mutations :as fm]))

(defn render-trace [this]
  (let [trace (-> this fp/props ::trace-data)
        container (gobj/get this "svgContainer")
        svg (gobj/get this "svg")]
    (gobj/set svg "innerHTML" "")
    (gobj/set this "renderedData"
      (renderPathomTrace svg
        (clj->js {:svgWidth  (gobj/get container "clientWidth")
                  :svgHeight (gobj/get container "clientHeight")
                  :data      trace})))))

(defn recompute-trace-size [this]
  (let [container (gobj/get this "svgContainer")]
    (updateTraceSize
      (doto (gobj/get this "renderedData")
        (gobj/set "svgWidth" (gobj/get container "clientWidth"))
        (gobj/set "svgHeight" (gobj/get container "clientHeight"))))))

(fp/defsc D3Trace [this _]
  {:css
   [[:.container {:flex 1
                  :max-width "100%"}]

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
     {:fill "#2900cc"}]

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
     {:position       "absolute"
      :pointer-events "none"
      :font-size      "12px"
      :font-family    "sans-serif"
      :background     "#fff"
      :padding        "1px 6px"
      :box-shadow     "#00000069 0px 1px 3px 0px"
      :white-space    "nowrap"
      :z-index        "10"}]]

   :componentDidMount
   (fn []
     (render-trace this)
     (js/addResizeListener (gobj/get this "svgContainer") #(recompute-trace-size this)))

   :componentDidUpdate
   (fn [prev-props _]
     (if (= (-> prev-props ::trace-data)
            (-> this fp/props ::trace-data))
       (recompute-trace-size this)
       (render-trace this)))}

  (dom/div :.container {:ref #(gobj/set this "svgContainer" %)}
    (dom/svg {:ref #(gobj/set this "svg" %)})))

(def d3-trace (fp/factory D3Trace))

(fp/defsc TraceView [this {:keys [expanded?]}]
  {:ident [:trace-id :trace-id]
   :query [:trace-id :trace-data :expanded?]
   :css   [[:.container {:width  "100%"
                         :height "300px"
                         :border "2px solid #da3939"}]
           [:.resized {:width  "80%"
                       :height "500px"}]]
   :componentDidMount
          (fn []
            (let [trace (-> this fp/props :trace-data)
                  container (gobj/get this "svgContainer")
                  svg (gobj/get this "svg")]
              (gobj/set this "renderedData"
                (renderPathomTrace svg
                  (clj->js {:svgWidth  (gobj/get container "clientWidth")
                            :svgHeight (gobj/get container "clientHeight")
                            :data      trace})))))

   :componentDidUpdate
          (fn [_ _]
            (let [container (gobj/get this "svgContainer")]
              (updateTraceSize
                (doto (gobj/get this "renderedData")
                  (gobj/set "svgWidth" (gobj/get container "clientWidth"))
                  (gobj/set "svgHeight" (gobj/get container "clientHeight"))))))}
  (dom/div
    (dom/button {:onClick #(fm/toggle! this :expanded?)} "Toggle Size")
    (dom/br)
    (dom/br)
    (dom/div :.container {:ref     #(gobj/set this "svgContainer" %)
                          :classes [(if expanded? :.resized)]}
      (dom/svg {:ref #(gobj/set this "svg" %)}))))

(def trace-view (fp/factory TraceView))
