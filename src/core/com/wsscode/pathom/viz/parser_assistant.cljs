(ns com.wsscode.pathom.viz.parser-assistant
  (:require [com.fulcrologic.fulcro.components :as fc]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.wsscode.pathom.viz.query-editor :as query.editor]
            [com.wsscode.pathom.viz.index-explorer :as index.explorer]
            [com.wsscode.pathom.viz.ui.kit :as ui]))

(fc/defsc ParserAssistant
  [this {:ui/keys  [query-editor]
         ::ui/keys [active-tab-id]}]
  {:pre-merge (fn [{:keys [current-normalized data-tree]}]
                (merge {::id               (random-uuid)
                        ::ui/active-tab-id ::query
                        :ui/query-editor   {}} current-normalized data-tree))
   :ident     ::id
   :query     [::id
               ::ui/active-tab-id
               {:ui/query-editor (fc/get-query query.editor/QueryEditor)}
               {:ui/index-explorer (fc/get-query index.explorer/IndexExplorer)}]}
  (ui/tab-container {}
    (ui/tab-nav {:classes           [:.border-collapse-bottom]
                 ::ui/active-tab-id active-tab-id
                 ::ui/target        this}
      [{::ui/tab-id ::query} "Query"]
      [{::ui/tab-id ::index-explorer} "Index Explorer"])
    (case active-tab-id
      ::query
      (query.editor/query-editor query-editor)

      ::index-explorer
      (dom/div "Index Explorer")

      (dom/div "Invalid page"))))

(def parser-assistant (fc/factory ParserAssistant {:keyfn ::id}))
