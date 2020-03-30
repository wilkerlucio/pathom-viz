(ns com.wsscode.pathom.viz.parser-assistant-cards
  (:require [cljs.spec.alpha :as s]
            [com.fulcrologic.fulcro.components :as fc]
            [com.fulcrologic.guardrails.core :refer [>def >defn >fdef => | <- ?]]
            [com.wsscode.async.async-cljs :refer [let-chan go-promise <!]]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.sugar :as ps]
            [com.wsscode.pathom.viz.client-parser :as cp]
            [com.wsscode.pathom.viz.helpers :as h]
            [com.wsscode.pathom.viz.parser-assistant :as assistant]
            [com.wsscode.pathom.viz.query-editor :as query.editor]
            [com.wsscode.pathom.viz.ui.kit :as ui]
            [nubank.workspaces.card-types.fulcro3 :as ct.fulcro]
            [nubank.workspaces.core :as ws]
            [nubank.workspaces.model :as wsm]
            [com.wsscode.pathom.viz.aux.demo-parser :as demo-parser]
            [cljs.core.async :as async]))

(def registry [cp/client-parser-mutation])

(def parser
  (p/async-parser
    {::p/env     {::p/reader               [p/map-reader
                                            pc/reader3
                                            pc/open-ident-reader
                                            p/env-placeholder-reader]
                  ::p/placeholder-prefixes #{">"}}
     ::p/mutate  pc/mutate-async
     ::p/plugins [(pc/connect-plugin {::pc/register registry})
                  p/error-handler-plugin
                  p/trace-plugin]}))

(def client-parsers
  (atom {:base demo-parser/parser}))

(ws/defcard parser-assistant-card
  {::wsm/align ::wsm/stretch-flex}
  (ct.fulcro/fulcro-card
    {::ct.fulcro/root          assistant/ParserAssistant
     ::ct.fulcro/initial-state {::assistant/id     "assistant"
                                :ui/query-editor   {::cp/parser-id       :base
                                                    ::query.editor/id    "singleton"
                                                    ::query.editor/query "[:answer]"}
                                :ui/index-explorer {:com.wsscode.pathom.viz.index-explorer/id "singleton"}}
     ::ct.fulcro/app           {:client-did-mount
                                (fn [app]
                                  (assistant/initialize-parser-assistent app))

                                :remotes
                                {:remote
                                 (h/pathom-remote #(parser (assoc % ::cp/parsers @client-parsers) %2))}}}))
