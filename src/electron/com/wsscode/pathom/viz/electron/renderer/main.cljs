(ns com.wsscode.pathom.viz.electron.renderer.main
  (:require [com.wsscode.pathom.viz.parser-assistant :as assistant]
            [com.fulcrologic.fulcro.application :as fapp]
            [com.wsscode.pathom.viz.client-parser :as cp]
            [com.wsscode.pathom.viz.query-editor :as query.editor]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.viz.aux.demo-parser :as demo-parser]
            [com.wsscode.pathom.viz.helpers :as pvh]
            [com.fulcrologic.fulcro-css.css-injection :as cssi]
            [com.fulcrologic.fulcro.components :as fc]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.wsscode.pathom.viz.ui.kit :as ui]))

(defonce electron (js/require "electron"))
(defonce shell (.-shell electron))

(defn after-load []
  (js/console.log "Done reloading"))

(def registry
  [cp/registry query.editor/registry])

(def client-parsers
  (atom {::assistant/singleton demo-parser/parser}))

(def parser
  (p/async-parser
    {::p/env     {::p/reader               [p/map-reader
                                            pc/reader3
                                            pc/open-ident-reader
                                            p/env-placeholder-reader]
                  ::cp/parsers*            client-parsers
                  ::p/placeholder-prefixes #{">"}}
     ::p/mutate  pc/mutate-async
     ::p/plugins [(pc/connect-plugin {::pc/register registry})
                  p/error-handler-plugin
                  p/elide-special-outputs-plugin
                  p/trace-plugin]}))

(defonce app
  (fapp/fulcro-app
    {:remotes
     {:remote
      (pvh/pathom-remote #(parser (assoc % ::cp/parsers @client-parsers) %2))}}))

(fc/defsc Root
  [this {:ui/keys [multi-parser]}]
  {:pre-merge (fn [{:keys [current-normalized data-tree]}]
                (merge {:ui/multi-parser {}} current-normalized data-tree))
   :query     [{:ui/multi-parser (fc/get-query assistant/MultiParserManager)}]
   :css       [[:body {:margin "0"}]
               [:#app-root {:width      "100vw"
                            :height     "100vh"
                            :box-sizing "border-box"
                            ;:padding    "10px"
                            :overflow   "hidden"
                            :display    "flex"}]
               [:.footer {:background "#eee"
                          :display    "flex"
                          :padding    "6px 10px"
                          :text-align "right"}
                ui/text-sans-13
                [:a {:text-decoration "none"}]]]}
  (ui/column (ui/gc :.flex)
    (assistant/multi-parser-manager multi-parser)
    (dom/div :.footer
      (dom/a {:href    "#"
              :onClick (ui/prevent-default #(.openExternal shell "https://github.com/wilkerlucio/pathom-viz"))}
        "Pathom Viz")
      (dom/div (ui/gc :.flex))
      (dom/div "Freely distributed by "
        (dom/a {:href    "#"
                :onClick (ui/prevent-default #(.openExternal shell "https://github.com/wilkerlucio"))}
          "Wilker Lucio")))))

(def root (fc/factory Root {:keyfn ::id}))

(defn init []
  (fapp/mount! app Root "app-root")
  (cssi/upsert-css "pathom-viz" {:component Root}))

(init)
