(ns com.wsscode.pathom.viz.parser-assistant-cards
  (:require [com.fulcrologic.guardrails.core :refer [>def >defn >fdef => | <- ?]]
            [com.wsscode.async.async-cljs :refer [let-chan go-promise <!]]
            [com.wsscode.pathom.viz.helpers :as h]
            [com.wsscode.pathom.viz.local-parser :as local.parser]
            [com.wsscode.pathom.viz.parser-assistant :as assistant]
            [com.wsscode.pathom.viz.query-editor :as query.editor]
            [nubank.workspaces.card-types.fulcro3 :as ct.fulcro]
            [nubank.workspaces.core :as ws]
            [nubank.workspaces.model :as wsm]))

(ws/defcard parser-assistant-card
  {::wsm/align ::wsm/stretch-flex}
  (ct.fulcro/fulcro-card
    {::ct.fulcro/root          assistant/ParserAssistant
     ::ct.fulcro/initial-state {::assistant/assistant-id "singleton"
                                :ui/query-editor         {::query.editor/id "singleton"}
                                :ui/index-explorer       {:com.wsscode.pathom.viz.index-explorer/id "singleton"}}
     ::ct.fulcro/app           {:remotes
                                {:remote
                                 (h/pathom-remote (local.parser/parser []))}}}))

(ws/defcard multi-parser-manager-card
  {::wsm/align ::wsm/stretch-flex}
  (ct.fulcro/fulcro-card
    {::ct.fulcro/root          assistant/MultiParserManager
     ::ct.fulcro/initial-state {::assistant/manager-id "singleton"}
     ::ct.fulcro/app           {:remotes
                                {:remote
                                 (h/pathom-remote (local.parser/parser []))}}}))
