(ns com.wsscode.pathom.viz.embed.main
  "Remote helpers to call remote parsers from the client."
  (:require
    ["react-dom" :as react-dom]
    [cljs.reader :refer [read-string]]
    [com.wsscode.pathom.viz.embed.messaging :as p.viz.msg]
    [com.wsscode.pathom.viz.lib.hooks :as p.hooks]
    [helix.core :as h]
    [helix.dom :as dom]
    [com.wsscode.tailcatcss.core :as tail-cat]
    [com.wsscode.pathom.viz.trace-with-plan :as trace+plan]
    [com.wsscode.pathom3.interface.eql :as p.eql]
    [com.wsscode.pathom3.connect.indexes :as pci]
    [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
    [com.wsscode.pathom.viz.trace :as pvt]))

(h/defnc EmbedTrace [{:keys [data]}]
  (let [data (p.eql/process (pci/register (pbir/constantly-resolver :foo "bar"))
               [:foo])]
   (trace+plan/trace-with-plan data)))

(def component-map
  {"trace" EmbedTrace})

(defn render-child-component [{:keys [component-name component-props]}]
  (if-let [Comp (get component-map component-name)]
    (h/$ Comp {:data component-props})
    (dom/div "Can't find component " component-name)))

(def full-css
  (into tail-cat/everything pvt/trace-css))

(h/defnc PathomVizEmbed []
  (let [component-contents! (p.hooks/use-fstate (p.viz.msg/query-param-state))]
    (p.hooks/use-garden-css full-css)
    (p.viz.msg/use-post-message-data component-contents!)

    (if @component-contents!
      (render-child-component @component-contents!)
      (dom/noscript))))

(defn start []
  (react-dom/render (h/$ PathomVizEmbed {}) (js/document.getElementById "app")))

(start)
