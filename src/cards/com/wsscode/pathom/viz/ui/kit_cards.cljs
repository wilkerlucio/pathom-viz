(ns com.wsscode.pathom.viz.ui.kit-cards
  (:require [nubank.workspaces.lib.fulcro-portal :as f.portal]
            [nubank.workspaces.card-types.fulcro :as ct.fulcro]
            [nubank.workspaces.card-types.react :as ct.react]
            [nubank.workspaces.core :as ws]
            [nubank.workspaces.model :as wsm]
            [com.wsscode.pathom.viz.ui.kit :as ui]
            [fulcro.client.localized-dom :as dom]))

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
  (let [state (atom 0)]
    (ct.react/react-card state
      (ui/number-input {:value    @state
                        :onChange (fn [_ n]
                                    (js/console.log "RESET!" n)
                                    (reset! state n))}))))

(f.portal/add-component-css! ui/UIKit)
