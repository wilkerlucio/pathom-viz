(ns com.wsscode.pathom.viz.codemirror6-cards
  (:require [nubank.workspaces.card-types.react :as ct.react]
            [nubank.workspaces.core :as ws]
            [nubank.workspaces.model :as wsm]
            [com.wsscode.pathom.viz.codemirror6 :as cm6]
            [helix.core :as h]))

(ws/defcard editor-card
  (ct.react/react-card
    (h/$ cm6/Editor {:source "(foo \"bar\")"})))


