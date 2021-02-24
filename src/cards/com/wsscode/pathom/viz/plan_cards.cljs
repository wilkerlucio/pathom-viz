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
                {:reference.routing-number/bank-name
                 {#:reference.routing-number{:routing-number
                                             {}}
                  #{employer-api.pathom-api/routing-number-resolver}},
                 :gravie.bank-account/id
                 {#:gravie.bank-account{:bank-account-id
                                        {}}
                  #{employer-api.pathom-api/bank-account-resolver}},
                 :gravie.address/address-id
                 {#:gravie.employer{:id {}}
                  #{employer-api.pathom-api/employer-by-id-resolver},
                  #:gravie.employer{:external-id
                                    {}}
                  #{employer-api.pathom-api/employer-by-external-id-resolver},
                  #:gravie.employer{:name {}}
                  #{employer-api.pathom-api/employer-by-name-resolver}},
                 :gravie.contact/id
                 {#:gravie.employer{:id {}}
                  #{employer-api.pathom-api/employer-by-id-resolver},
                  #:gravie.employer{:external-id
                                    {}}
                  #{employer-api.pathom-api/employer-by-external-id-resolver},
                  #:gravie.employer{:name {}}
                  #{employer-api.pathom-api/employer-by-name-resolver}},
                 :gravie.broker/name
                 {#:gravie.broker{:id {}}
                  #{employer-api.pathom-api/broker-by-id-resolver}},
                 :gravie.employer/external-id
                 {#:gravie.employer{:id {}}
                  #{employer-api.pathom-api/employer-by-id-resolver},
                  #:gravie.employer{:name {}}
                  #{employer-api.pathom-api/employer-by-name-resolver}},
                 :gravie.employer/active
                 {#:gravie.employer{:id {}}
                  #{employer-api.pathom-api/employer-by-id-resolver},
                  #:gravie.employer{:external-id
                                    {}}
                  #{employer-api.pathom-api/employer-by-external-id-resolver},
                  #:gravie.employer{:name {}}
                  #{employer-api.pathom-api/employer-by-name-resolver}},
                 :gravie.employer/id
                 {#:gravie.employer{:external-id
                                    {}}
                  #{employer-api.pathom-api/employer-by-external-id-resolver},
                  #:gravie.employer{:name {}}
                  #{employer-api.pathom-api/employer-by-name-resolver}},
                 :gravie.broker/type
                 {#:gravie.broker{:type-string
                                  {}}
                  #{gravie.broker_SLASH_type_string->gravie.broker_SLASH_type-single-attr-transform}},
                 :gravie.employer-user/id
                 {#:gravie.employer{:id {}}
                  #{employer-api.pathom-api/employer-user-resolver}},
                 :gravie.bank-account/type-string
                 {#:gravie.bank-account{:bank-account-id
                                        {}}
                  #{employer-api.pathom-api/bank-account-resolver}},
                 :gravie.bank-account/bank-name
                 {#:reference.routing-number{:bank-name
                                             {}}
                  #{reference.routing_number_SLASH_bank_name->gravie.bank_account_SLASH_bank_name-alias}},
                 :gravie.address/zip-code
                 {#:gravie.address{:address-id
                                   {}}
                  #{employer-api.pathom-api/address-resolver}},
                 :gravie.bank-account/bank-account-id
                 {#:gravie.employer{:id {}}
                  #{employer-api.pathom-api/employer-by-id-resolver},
                  #:gravie.employer{:external-id
                                    {}}
                  #{employer-api.pathom-api/employer-by-external-id-resolver},
                  #:gravie.employer{:name {}}
                  #{employer-api.pathom-api/employer-by-name-resolver},
                  #:gravie.broker{:id {}}
                  #{employer-api.pathom-api/broker-by-id-resolver}},
                 :gravie.broker/type-string
                 {#:gravie.broker{:id {}}
                  #{employer-api.pathom-api/broker-by-id-resolver}},
                 :gravie.bank-account/account-number
                 {#:gravie.bank-account{:bank-account-id
                                        {}}
                  #{employer-api.pathom-api/bank-account-resolver}},
                 :gravie.bank-account/routing-number
                 {#:reference.routing-number{:routing-number
                                             {}}
                  #{reference.routing_number_SLASH_routing_number->gravie.bank_account_SLASH_routing_number-alias}},
                 :gravie.address/city
                 {#:gravie.address{:address-id
                                   {}}
                  #{employer-api.pathom-api/address-resolver}},
                 :gravie.address/id
                 {#:gravie.address{:address-id
                                   {}}
                  #{employer-api.pathom-api/address-resolver}},
                 :gravie.contact/email
                 {#:gravie.contact{:id {}}
                  #{employer-api.pathom-api/contact-resolver}},
                 :gravie.address/address-2
                 {#:gravie.address{:address-id
                                   {}}
                  #{employer-api.pathom-api/address-resolver}},
                 :gravie.address/address-1
                 {#:gravie.address{:address-id
                                   {}}
                  #{employer-api.pathom-api/address-resolver}},
                 :gravie.employer-user/email
                 {#:gravie.employer{:id {}}
                  #{employer-api.pathom-api/employer-user-resolver}},
                 :reference.routing-number/routing-number
                 {#:gravie.bank-account{:bank-account-id
                                        {}}
                  #{employer-api.pathom-api/bank-account-resolver}},
                 :gravie.address/state-code
                 {#:gravie.address{:state-code-string
                                   {}}
                  #{gravie.address_SLASH_state_code_string->gravie.address_SLASH_state_code-single-attr-transform}},
                 :gravie.employer/minimum-enrollees
                 {#:gravie.employer{:id {}}
                  #{employer-api.pathom-api/employer-by-id-resolver},
                  #:gravie.employer{:external-id
                                    {}}
                  #{employer-api.pathom-api/employer-by-external-id-resolver},
                  #:gravie.employer{:name {}}
                  #{employer-api.pathom-api/employer-by-name-resolver}},
                 :gravie.employer/tin
                 {#:gravie.employer{:id {}}
                  #{employer-api.pathom-api/employer-by-id-resolver},
                  #:gravie.employer{:external-id
                                    {}}
                  #{employer-api.pathom-api/employer-by-external-id-resolver},
                  #:gravie.employer{:name {}}
                  #{employer-api.pathom-api/employer-by-name-resolver}},
                 :reference.county/fips-code
                 {#:gravie.address{:address-id
                                   {}}
                  #{employer-api.pathom-api/address-resolver}},
                 :gravie.bank-account/type
                 {#:gravie.bank-account{:type-string
                                        {}}
                  #{gravie.bank_account_SLASH_type_string->gravie.bank_account_SLASH_type-single-attr-transform}},
                 :gravie.address/state-code-string
                 {#:gravie.address{:address-id
                                   {}}
                  #{employer-api.pathom-api/address-resolver}},
                 :gravie.employer/name
                 {#:gravie.employer{:id {}}
                  #{employer-api.pathom-api/employer-by-id-resolver},
                  #:gravie.employer{:external-id
                                    {}}
                  #{employer-api.pathom-api/employer-by-external-id-resolver}},
                 :reference.county/name
                 {#:reference.county{:fips-code
                                     {}}
                  #{employer-api.pathom-api/county-resolver}},
                 :reference.county/state-code
                 {#:reference.county{:fips-code
                                     {}}
                  #{employer-api.pathom-api/county-resolver}}}

                :com.wsscode.pathom3.connect.planner/available-data
                {:gravie.employer/id {}}

                ::eql/query
                [:gravie.employer/name
                 :gravie.employer/id
                 :gravie.employer/external-id
                 :gravie.employer/active
                 :gravie.employer-user/email
                 :gravie.address/address-1
                 :gravie.address/state-code
                 :gravie.bank-account/account-number
                 :reference.routing-number/bank-name
                 :reference.routing-number/routing-number
                 :gravie.bank-account/routing-number
                 :gravie.bank-account/bank-name
                 :gravie.contact/email
                 :reference.county/name]})
            (mapv (juxt identity viz-plan/compute-plan-elements)))

       :display
       ::viz-plan/display-type-node-id})))
