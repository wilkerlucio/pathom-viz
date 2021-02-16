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
       (->> (viz-plan/compute-frames
              '{::pci/index-oir
                {:foo.bank-account/routing-number   {#:foo.routing-number{:routing-number {}} #{foo.routing_number_SLASH_routing_number->foo.bank_account_SLASH_routing_number-alias}},
                 :foo.contact/id                    {#:foo.employer{:id {}}          #{com.wsscode.pathom3.demos.debug/employer-by-id-resolver},
                                                     #:foo.employer{:external-id {}} #{com.wsscode.pathom3.demos.debug/employer-by-external-id-resolver}},
                 :foo.bank-account/bank-account-id  {#:foo.employer{:id {}}          #{com.wsscode.pathom3.demos.debug/employer-by-id-resolver},
                                                     #:foo.employer{:external-id {}} #{com.wsscode.pathom3.demos.debug/employer-by-external-id-resolver}},
                 :foo.employer/id                   {#:foo.employer{:external-id {}} #{com.wsscode.pathom3.demos.debug/employer-by-external-id-resolver}},
                 :foo.bank-account/id               {#:foo.bank-account{:bank-account-id {}} #{com.wsscode.pathom3.demos.debug/bank-account-resolver}},
                 :foo.routing-number/routing-number {#:foo.bank-account{:bank-account-id {}} #{com.wsscode.pathom3.demos.debug/bank-account-resolver}},
                 :foo.contact/email                 {#:foo.contact{:id {}} #{com.wsscode.pathom3.demos.debug/contact-resolver}},
                 :foo.routing-number/bank-name      {#:foo.routing-number{:routing-number {}} #{com.wsscode.pathom3.demos.debug/routing-number-resolver}}}

                :com.wsscode.pathom3.connect.planner/available-data
                {:foo.employer/external-id {}}

                ::eql/query
                [:foo.routing-number/bank-name
                 :foo.bank-account/routing-number
                 :foo.contact/email]})
            (mapv (juxt identity viz-plan/compute-plan-elements)))

       :display
       ::viz-plan/display-type-node-id})))
