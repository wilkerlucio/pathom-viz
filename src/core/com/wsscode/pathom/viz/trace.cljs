(ns com.wsscode.pathom.viz.trace
  (:require ["./d3-trace" :refer [renderPathomTrace updateTraceSize]]
            [fulcro.client.localized-dom :as dom]
            [fulcro.client.primitives :as fp]
            [goog.object :as gobj]
            [fulcro.client.mutations :as fm]))

(fp/defsc D3Trace [this _]
  {:css         []
   :css-include []}
  (dom/div))

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
            (let [trace     (-> this fp/props :trace-data)
                  container (gobj/get this "svgContainer")
                  svg       (gobj/get this "svg")]
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
