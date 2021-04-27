(ns com.wsscode.pathom.viz.plan-cards
  (:require [com.wsscode.pathom3.viz.plan :as viz-plan]
            [nubank.workspaces.card-types.react :as ct.react]
            [nubank.workspaces.core :as ws]
            [nubank.workspaces.model :as wsm]
            [helix.core :refer [$]]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [edn-query-language.core :as eql]))



(ws/defcard plan-snapshots-card
  {::wsm/align ::wsm/stretch-flex}
  (ct.react/react-card
    ($ viz-plan/PlanSnapshots
      {:frames
       (viz-plan/compute-frames
         '{::pci/index-oir
           {}

           :com.wsscode.pathom3.connect.planner/available-data
           {}

           ::eql/query
           []})

       :display
       ::viz-plan/display-type-node-id})))
