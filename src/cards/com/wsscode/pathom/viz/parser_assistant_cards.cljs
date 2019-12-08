(ns com.wsscode.pathom.viz.parser-assistant-cards
  (:require [com.fulcrologic.fulcro.components :as fc]
            [com.wsscode.pathom.viz.parser-assistant :as assistant]
            [com.wsscode.pathom.viz.ui.kit :as ui]
            [com.wsscode.pathom.viz.helpers :as h]
            [com.wsscode.pathom.sugar :as ps]
            [nubank.workspaces.card-types.fulcro3 :as ct.fulcro]
            [nubank.workspaces.core :as ws]
            [nubank.workspaces.model :as wsm]
            [com.wsscode.pathom.viz.query-editor :as query.editor]))

(ws/defcard parser-assistant-card
  {::wsm/align ::wsm/stretch-flex}
  (ct.fulcro/fulcro-card
    {::ct.fulcro/root assistant/ParserAssistant
     ::ct.fulcro/app  {:remotes
                       {query.editor/remote-key
                        (h/pathom-remote
                          (ps/connect-async-parser
                            []))

                        :remote
                        (h/pathom-remote
                          (ps/connect-async-parser {}))}}}))
