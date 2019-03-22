(ns com.wsscode.pathom.viz.ui.kit-cards
  (:require [nubank.workspaces.lib.fulcro-portal :as f.portal]
            [nubank.workspaces.card-types.fulcro :as ct.fulcro]
            [nubank.workspaces.card-types.react :as ct.react]
            [nubank.workspaces.core :as ws]
            [nubank.workspaces.model :as wsm]
            [com.wsscode.pathom.viz.ui.kit :as ui]
            [fulcro.client.localized-dom :as dom]
            [cljs.spec.alpha :as s]))

(ws/defcard collapsible-box-card
  {::wsm/align {:width "100%"}}
  (let [state (atom false)]
    (ct.react/react-card state
      (ui/collapsible-box {::ui/title      "Panel Header"
                           ::ui/collapsed? @state
                           ::ui/on-toggle  #(reset! state %)}
        (dom/div "Whatever")))))

(ws/defcard number-input-card
  {::wsm/align {:width "100%"}}
  (let [state (atom 1)]
    (ct.react/react-card state
      (ui/number-input {:value    @state
                        :onChange (fn [_ n]
                                    (js/console.log "RESET!" n)
                                    (reset! state n))}))))

#_ #_ #_ #_
(defn nob-card [{::keys [nobs initial-state]} f]
  (let [nob-state (atom {})
        state (atom initial-state)]
    (ct.react/react-card* state
      (fn []
        (dom/div {}
          (f nob-values state)
          (dom/div
            ))))))

(s/def ::min int?)
(s/def ::max int?)

(ws/defcard number-input-card-with-nobs
  {::wsm/align {:width "100%"}}
  (let [state (atom 1)]
    (nob-card {::nobs          {::min {}
                                ::max {}}
               ::initial-state 1}
      (fn [{::keys [min max]} state]
        (ui/number-input {:value    @state
                          :min      min
                          :max      max
                          :onChange (fn [_ n]
                                      (js/console.log "RESET!" n)
                                      (reset! state n))})))))

(f.portal/add-component-css! ui/UIKit)
