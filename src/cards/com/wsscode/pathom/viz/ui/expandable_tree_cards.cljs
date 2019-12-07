(ns com.wsscode.pathom.viz.ui.expandable-tree-cards
  (:require [nubank.workspaces.card-types.fulcro3 :as ct.fulcro]
            [nubank.workspaces.core :as ws]
            [nubank.workspaces.model :as wsm]
            [com.wsscode.pathom.viz.ui.expandable-tree :as ex-tree]
            [fulcro.client.localized-dom :as dom]
            [edn-query-language.core :as eql]))

(ws/defcard expandable-tree-card
  {::wsm/card-width 4 ::wsm/card-height 12}
  (ct.fulcro/fulcro-card
    {::ct.fulcro/root
     ex-tree/ExpandableTree

     ::ct.fulcro/computed
     {::ex-tree/render
      (fn [{:keys [key] :as x}]
        (dom/div {:onClick #(js/console.log x)} (pr-str key)))

      ::ex-tree/root
      (eql/query->ast
        [:foo
         {:bar
          [:baz]}
         {:more
          [{:deep [{:inside [:more]}]}
           :with]}])}}))
