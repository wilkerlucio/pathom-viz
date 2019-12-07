(ns com.wsscode.pathom.viz.codemirror-cards
  (:require [cljs.core.async :as async]
            [com.wsscode.common.async-cljs :refer [<? go-catch]]
            [com.wsscode.pathom.viz.codemirror :as cm]
            [nubank.workspaces.card-types.fulcro3 :as ct.fulcro]
            [nubank.workspaces.core :as ws]
            [com.fulcrologic.fulcro.components :as fc]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.fulcrologic.fulcro.mutations :as fm]))

(fc/defsc FulcroDemo
  [this {:keys [counter]}]
  {:initial-state (fn [_] {:counter 0})
   :ident         (fn [] [::id "singleton"])
   :query         [:counter]}
  (dom/div
    (str "Fulcro counter demo [" counter "]")
    (dom/button {:onClick #(fm/set-value! this :counter (inc counter))} "+")))

(ws/defcard fulcro-demo-card
  (ct.fulcro/fulcro-card
    {::ct.fulcro/root FulcroDemo}))
