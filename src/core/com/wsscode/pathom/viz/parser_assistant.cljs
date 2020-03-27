(ns com.wsscode.pathom.viz.parser-assistant
  (:require [com.fulcrologic.fulcro.components :as fc]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.wsscode.pathom.viz.query-editor :as query.editor]
            [com.wsscode.pathom.viz.index-explorer :as index.explorer]
            [com.wsscode.pathom.viz.client-parser :as cp]))

(fc/defsc ParserAssistant
  [this {::keys [] :ui/keys [query-editor]}]
  {:pre-merge (fn [{:keys [current-normalized data-tree]}]
                (merge {::id             (random-uuid)
                        :ui/query-editor {}} current-normalized data-tree))
   :ident     ::id
   :query     [::id
               {:ui/query-editor (fc/get-query query.editor/QueryEditor)}
               {:ui/index-explorer (fc/get-query index.explorer/IndexExplorer)}]}
  (query.editor/query-editor query-editor))

(def parser-assistant (fc/factory ParserAssistant {:keyfn ::id}))
