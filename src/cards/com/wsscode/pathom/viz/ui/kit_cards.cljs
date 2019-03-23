(ns com.wsscode.pathom.viz.ui.kit-cards
  (:require [nubank.workspaces.lib.fulcro-portal :as f.portal]
            [nubank.workspaces.card-types.fulcro :as ct.fulcro]
            [nubank.workspaces.card-types.react :as ct.react]
            [nubank.workspaces.core :as ws]
            [nubank.workspaces.model :as wsm]
            [com.wsscode.pathom.viz.ui.kit :as ui]
            [fulcro.client.localized-dom :as dom]
            [cljs.test :refer-macros [is are run-tests async testing]]
            [cljs.spec.alpha :as s]))

(ws/deftest test-merge-with-mergers []
  (is (= (ui/merge-with-mergers {}
           {:a 1 :b 2} {:a 2 :c 3})
         {:a 2 :b 2 :c 3}))
  (is (= (ui/merge-with-mergers {:a +}
           {:a 1 :b 2} {:a 2 :c 3})
         {:a 3 :b 2 :c 3})))

(ws/deftest test-dom-props
  (is (= (ui/dom-props {}) {}))
  (is (= (ui/dom-props {:foo "bar"}) {:foo "bar"}))
  (is (= (ui/dom-props {::ns "gone"}) {}))
  (is (= (ui/dom-props {::ns "gone" :foo "bar"}) {:foo "bar"}))
  (is (= (ui/dom-props {:classes [:.default]} {:extra "data"})
         {:classes [:.default] :extra "data"}))
  (is (= (ui/dom-props {:classes [:.default]} {:classes [:.more]})
         {:classes [:.default :.more]})))

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
