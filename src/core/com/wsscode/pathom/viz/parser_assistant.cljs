(ns com.wsscode.pathom.viz.parser-assistant
  (:require [com.fulcrologic.fulcro.components :as fc]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.wsscode.pathom.viz.query-editor :as query.editor]
            [com.wsscode.pathom.viz.index-explorer :as index.explorer]
            [com.wsscode.pathom.viz.ui.kit :as ui]
            [com.wsscode.pathom.viz.client-parser :as cp]))

(defn initialize-parser-assistent [app]
  (query.editor/load-indexes app {::query.editor/id "singleton"
                                  ::cp/parser-id    :base})
  (index.explorer/load-indexes app {::index.explorer/id "singleton"
                                    ::cp/parser-id      :base}))

(fc/defsc ParserAssistant
  [this {:ui/keys  [query-editor index-explorer]
         ::ui/keys [active-tab-id]}]
  {:pre-merge (fn [{:keys [current-normalized data-tree]}]
                (merge {::id               (random-uuid)
                        ::ui/active-tab-id ::query
                        :ui/query-editor   {}
                        :ui/index-explorer {}}
                  current-normalized data-tree))
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
      (index.explorer/index-explorer index-explorer)

      (dom/div "Invalid page"))))

(def parser-assistant (fc/factory ParserAssistant {:keyfn ::id}))
