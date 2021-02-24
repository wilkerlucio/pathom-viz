(ns com.wsscode.pathom.viz.embed.main
  "Remote helpers to call remote parsers from the client."
  (:require
    ["react-dom" :as react-dom]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.wsscode.pathom.viz.embed.fulcro-float-roots]
    [com.wsscode.pathom.viz.embed.helix-state-play]
    [com.wsscode.pathom.viz.embed.messaging :as p.viz.msg]
    [com.wsscode.pathom.viz.lib.hooks :as p.hooks]
    [com.wsscode.pathom.viz.trace :as pvt]
    [com.wsscode.pathom.viz.trace-with-plan :as trace+plan]
    [com.wsscode.tailwind-garden.core :as tailwind]
    [com.wsscode.pathom.viz.index-explorer :as index-explorer]
    [helix.core :as h]
    [helix.hooks :as hooks]))

(h/defnc EmbedTrace [{:keys [data]}]
  (trace+plan/trace-with-plan data))

(h/defnc IndexExplorer [{:keys [data]}]
  (trace+plan/trace-with-plan data))

(def component-map
  {"trace"          EmbedTrace
   "index-explorer" IndexExplorer})

(defn render-child-component [{:keys [component-name component-props]}]
  (if-let [Comp (get component-map component-name)]
    (h/$ Comp {:data component-props})
    (dom/div "Can't find component " component-name)))

(def full-css
  (into pvt/trace-css (tailwind/everything)))

(comment
  (js/console.log "!! " full-css))

(h/defnc PathomVizEmbed []
  (let [component-contents! (p.hooks/use-fstate (p.viz.msg/query-param-state))]

    (p.viz.msg/use-post-message-data component-contents!)

    (or (p.hooks/use-garden-css full-css)
        (if @component-contents!
          (render-child-component @component-contents!)
          (dom/noscript)))))

(defn start []
  (react-dom/render
    (h/$ PathomVizEmbed {})
    (js/document.getElementById "app")))

#_ (start)

(com.wsscode.pathom.viz.embed.helix-state-play/start)
