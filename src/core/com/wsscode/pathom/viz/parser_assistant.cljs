(ns com.wsscode.pathom.viz.parser-assistant
  (:require ["react" :refer [useEffect]]
            [com.fulcrologic.fulcro.components :as fc]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.wsscode.pathom.viz.query-editor :as query.editor]
            [com.wsscode.pathom.viz.index-explorer :as index.explorer]
            [com.wsscode.pathom.viz.ui.kit :as ui]
            [com.wsscode.pathom.viz.client-parser :as cp]))

(defn initialize-parser-assistent [this]
  (let [{::cp/keys [parser-id] :as props} (fc/props this)]
    (query.editor/load-indexes this {::query.editor/id (-> props :ui/query-editor ::query.editor/id)
                                     ::cp/parser-id    parser-id})
    (index.explorer/load-indexes this {::index.explorer/id (-> props :ui/index-explorer ::index.explorer/id)
                                       ::cp/parser-id      parser-id})))

(fc/defsc ParserAssistant
  [this {:ui/keys  [query-editor index-explorer]
         ::ui/keys [active-tab-id]}]
  {:pre-merge  (fn [{:keys [current-normalized data-tree]}]
                 (merge {::id               (random-uuid)
                         ::cp/parser-id     :base
                         ::ui/active-tab-id ::query
                         :ui/query-editor   {}
                         :ui/index-explorer {}}
                   current-normalized data-tree))
   :ident      ::id
   :query      [::id
                ::ui/active-tab-id
                ::cp/parser-id
                {:ui/query-editor (fc/get-query query.editor/QueryEditor)}
                {:ui/index-explorer (fc/get-query index.explorer/IndexExplorer)}]
   :use-hooks? true}
  (useEffect
    (fn []
      (initialize-parser-assistent this)

      js/undefined)
    #js [true])

  (fc/with-parent-context this
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

        (dom/div "Invalid page")))))

(def parser-assistant (fc/factory ParserAssistant {:keyfn ::id}))
