(ns com.wsscode.pathom.viz.react-hooks-cards
  (:require [nubank.workspaces.card-types.fulcro3 :as ct.fulcro]
            [nubank.workspaces.card-types.react :as ct.react]
            [nubank.workspaces.core :as ws]
            [nubank.workspaces.model :as wsm]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.fulcrologic.fulcro.components :as fc]
            [com.wsscode.pathom.viz.helpers :as h]))

(defn hook-demo-card [f]
  (ct.fulcro/fulcro-card
    {::ct.fulcro/root
     (fc/configure-hooks-component!
       f
       {:componentName (keyword (gensym "hook-demo"))})

     ::ct.fulcro/wrap-root?
     false}))

(ws/defcard use-state-card
  (hook-demo-card
    (fn []
      (let [[count set-count!] (h/use-state 0)]
        (dom/button {:onClick #(set-count! inc)}
          (str "Counter: " count))))))

(ws/defcard use-state-lazy-card
  (hook-demo-card
    (fn []
      (let [[count set-count!] (h/use-state (fn [] 0))]
        (dom/button {:onClick #(set-count! inc)}
          (str "Counter: " count))))))

(ws/defcard use-atom-state-card
  (hook-demo-card
    (fn []
      (let [count* (h/use-atom-state 0)]
        (dom/button {:onClick #(swap! count* inc)}
          (str "Counter: " @count*))))))

(ws/defcard use-atom-state-lazy-card
  (hook-demo-card
    (fn []
      (let [count* (h/use-atom-state (fn [] 0))]
        (dom/button {:onClick #(swap! count* inc)}
          (str "Counter: " @count*))))))

(ws/defcard use-atom-state-card
  (hook-demo-card
    (fn []
      (let [count* (h/use-atom-state 0)]
        (dom/button {:onClick #(swap! count* inc)}
          (str "Counter: " @count*))))))

(ws/defcard use-persistent-state-card
  (hook-demo-card
    (fn []
      (let [count* (h/use-persistent-state ::counter 0)]
        (dom/button {:onClick #(reset! count* (inc @count*))}
          (str "Counter: " @count*))))))

(ws/defcard use-effect-card
  (hook-demo-card
    (fn []
      (h/use-effect (fn [] (js/console.log "Mount")
                      (fn []
                        (js/console.log "Unmount"))))
      (dom/div "check console"))))

(ws/defcard use-effect-deps-card
  (hook-demo-card
    (fn []
      (let [count* (h/use-atom-state 0)]
        (h/use-effect (fn [] (js/console.log ">>>> Empty Deps")
                        (fn []
                          (js/console.log "<<<< Empty Deps")))
          [])
        (h/use-effect (fn [] (js/console.log ">>>> No deps")
                        (fn []
                          (js/console.log "<<<< No Deps"))))
        (h/use-effect (fn [] (js/console.log ">>>> Count Dep")
                        (fn []
                          (js/console.log "<<<< Count Dep")))
          [@count*])
        (dom/button {:onClick #(reset! count* (inc @count*))}
          (str "Click and check console: " @count*))))))

(def themes
  {:light {:background "#fff"
           :color      "#000"}
   :dark  {:background "#000"
           :color      "#fff"}})

(def SampleContext (h/create-context (:light themes)))

(fc/defsc ContextDemoThemedButton
  [this props]
  {:use-hooks? true}
  (let [theme (h/use-context SampleContext)]
    (dom/button {:style theme} (fc/children this))))

(def context-demo-themed-button (fc/factory ContextDemoThemedButton))

(fc/defsc ContextDemoToolbar
  [this {::keys []}]
  {:use-hooks? true}
  (dom/div
    (context-demo-themed-button {} "Button Content")))

(def context-demo-toolbar (fc/factory ContextDemoToolbar))

(fc/defsc ContextDemoApp
  [this {::keys []}]
  {:use-hooks? true}
  (dom/create-element (.-Provider SampleContext) #js {:value (:dark themes)}
    (context-demo-toolbar {})))

(def context-demo-app (fc/factory ContextDemoApp))

(ws/defcard use-context-card
  (hook-demo-card
    (fn []
      (context-demo-app {}))))