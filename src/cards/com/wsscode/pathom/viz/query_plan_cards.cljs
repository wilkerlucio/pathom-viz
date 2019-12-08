(ns com.wsscode.pathom.viz.query-plan-cards
  (:require [com.fulcrologic.fulcro.components :as fc]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.connect.planner :as pcp]
            [com.wsscode.pathom.sugar :as ps]
            [com.wsscode.pathom.viz.helpers :as h]
            [com.wsscode.pathom.viz.query-editor :as query.editor]
            [com.wsscode.pathom.viz.query-plan :as plan-view]
            [nubank.workspaces.card-types.fulcro3 :as ct.fulcro]
            [nubank.workspaces.core :as ws]
            [nubank.workspaces.model :as wsm]))

(fc/defsc QueryPlanWrapper
  [this {::pcp/keys [graph]}]
  {:pre-merge   (fn [{:keys [current-normalized data-tree]}]
                  (merge {::id (random-uuid)} current-normalized data-tree))
   :ident       ::id
   :css-include [plan-view/QueryPlanViz]
   :query       [::id ::pcp/graph]}
  (plan-view/query-plan-viz
    {::pcp/graph graph}))

(ws/defcard query-plan-card
  {::wsm/align ::wsm/stretch-flex}
  (ct.fulcro/fulcro-card
    {::ct.fulcro/root          QueryPlanWrapper
     ::ct.fulcro/initial-state {::pcp/graph
                                '{::pcp/nodes             {1 {::pc/sym               d
                                                              ::pcp/node-id          1
                                                              ::pcp/requires         {:d {}}
                                                              ::pcp/input            {:c {} :b {}}
                                                              ::pcp/after-nodes      #{6}
                                                              ::pcp/source-for-attrs #{:d}}
                                                           2 {::pc/sym               c
                                                              ::pcp/node-id          2
                                                              ::pcp/requires         {:c {}}
                                                              ::pcp/input            {:a {}}
                                                              ::pcp/after-nodes      #{6 3}
                                                              ::pcp/source-for-attrs #{:c}}
                                                           3 {::pc/sym               a
                                                              ::pcp/node-id          3
                                                              ::pcp/requires         {:a {}}
                                                              ::pcp/input            {:z {}}
                                                              ::pcp/after-nodes      #{4}
                                                              ::pcp/source-for-attrs #{:a}
                                                              ::pcp/run-next         6}
                                                           4 {::pc/sym               z
                                                              ::pcp/node-id          4
                                                              ::pcp/requires         {:z {}}
                                                              ::pcp/input            {}
                                                              ::pcp/source-for-attrs #{:z}
                                                              ::pcp/run-next         3}
                                                           5 {::pc/sym               b
                                                              ::pcp/node-id          5
                                                              ::pcp/requires         {:b {}}
                                                              ::pcp/input            {:a {}}
                                                              ::pcp/after-nodes      #{6}
                                                              ::pcp/source-for-attrs #{:b}}
                                                           6 {::pcp/node-id     6
                                                              ::pcp/requires    {:c {} :b {}}
                                                              ::pcp/run-and     #{2 5}
                                                              ::pcp/after-nodes #{3}
                                                              ::pcp/run-next    1}}
                                  ::pcp/index-syms        {d #{1} c #{2} a #{3} z #{4} b #{5}}
                                  ::pcp/unreachable-syms  #{}
                                  ::pcp/unreachable-attrs #{}
                                  ::pcp/index-attrs       {:z 4 :a 3 :c 2 :b 5 :d 1}
                                  ::pcp/root              4}}
     ::ct.fulcro/app           {:remotes
                                {query.editor/remote-key
                                 (h/pathom-remote
                                   (ps/connect-async-parser
                                     []))

                                 :remote
                                 (h/pathom-remote
                                   (ps/connect-async-parser {}))}}}))
