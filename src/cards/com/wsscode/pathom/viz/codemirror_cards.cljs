(ns com.wsscode.pathom.viz.codemirror-cards
  (:require [com.fulcrologic.fulcro.components :as fc]
            [com.wsscode.pathom.viz.codemirror :as cm]
            [com.wsscode.pathom.viz.ui.kit :as ui]
            [nubank.workspaces.card-types.fulcro3 :as ct.fulcro]
            [nubank.workspaces.core :as ws]
            [nubank.workspaces.model :as wsm]))

(fc/defsc ClojureCodeMirrorWrapper
  [this {::keys []}]
  {:pre-merge (fn [{:keys [current-normalized data-tree]}]
                (merge {::id (random-uuid)} current-normalized data-tree))
   :ident     ::id
   :query     [::id]
   :css       [[:.container {:position "relative"
                             :flex     1}]]}
  (cm/clojure {:value     ""
               :className (ui/ccss this :.container)}))

(ws/defcard codemirror-clojure-card
  {::wsm/align ::wsm/stretch-flex}
  (ct.fulcro/fulcro-card
    {::ct.fulcro/root ClojureCodeMirrorWrapper}))

(fc/defsc PathomCodeMirrorWrapper
  [this {::keys []}]
  {:pre-merge (fn [{:keys [current-normalized data-tree]}]
                (merge {::id (random-uuid)} current-normalized data-tree))
   :ident     ::id
   :query     [::id]
   :css       [[:.container {:position "relative"
                             :flex     1}]]}
  (cm/pathom {:value     ""
              :className (ui/ccss this :.container)}))

(ws/defcard codemirror-pathom-card
  {::wsm/align ::wsm/stretch-flex}
  (ct.fulcro/fulcro-card
    {::ct.fulcro/root PathomCodeMirrorWrapper}))
