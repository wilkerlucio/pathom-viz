(ns com.wsscode.pathom.viz.parser-assistant
  (:require [com.fulcrologic.fulcro.components :as fc]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.wsscode.pathom.viz.query-editor :as query.editor]
            [com.wsscode.pathom.viz.index-explorer :as index.explorer]))

(fc/defsc ParserAssistant
  [this {::keys []}]
  {:pre-merge (fn [{:keys [current-normalized data-tree]}]
                (merge {::id (random-uuid)} current-normalized data-tree))
   :ident     ::id
   :query     [::id
               {:ui/query-editor (fc/get-query query.editor/QueryEditor)}
               {:ui/index-explorer (fc/get-query index.explorer/IndexExplorer)}]}
  (dom/div))

(def parser-assistant (fc/factory ParserAssistant {:keyfn ::id}))
