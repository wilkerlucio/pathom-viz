(ns com.wsscode.pathom.viz.react-hooks-cards
  (:require [nubank.workspaces.card-types.fulcro3 :as ct.fulcro]
            [nubank.workspaces.card-types.react :as ct.react]
            [nubank.workspaces.core :as ws]
            [nubank.workspaces.model :as wsm]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.fulcrologic.fulcro.components :as fc]
            [com.wsscode.pathom.viz.helpers :as pvh]))

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
      (let [[count set-count!] (pvh/use-state 0)]
        (dom/button {:onClick #(set-count! inc)}
          (str "Counter: " count))))))

(ws/defcard use-state-lazy-card
  (hook-demo-card
    (fn []
      (let [[count set-count!] (pvh/use-state (fn [] 0))]
        (dom/button {:onClick #(set-count! inc)}
          (str "Counter: " count))))))

(ws/defcard use-atom-state-card
  (hook-demo-card
    (fn []
      (let [count* (pvh/use-atom-state 0)]
        (dom/button {:onClick #(swap! count* inc)}
          (str "Counter: " @count*))))))

(ws/defcard use-atom-state-lazy-card
  (hook-demo-card
    (fn []
      (let [count* (pvh/use-atom-state (fn [] 0))]
        (dom/button {:onClick #(swap! count* inc)}
          (str "Counter: " @count*))))))

(ws/defcard use-atom-state-card
  (hook-demo-card
    (fn []
      (let [count* (pvh/use-atom-state 0)]
        (dom/button {:onClick #(swap! count* inc)}
          (str "Counter: " @count*))))))

(ws/defcard use-persistent-state-card
  (hook-demo-card
    (fn []
      (let [count* (pvh/use-persistent-state ::counter 0)]
        (dom/button {:onClick #(reset! count* (inc @count*))}
          (str "Counter: " @count*))))))

(ws/defcard use-effect-card
  (hook-demo-card
    (fn []
      (let [count* (pvh/use-atom-state 0)]
        (dom/button {:onClick #(swap! count* inc)}
          (str "Counter: " @count*))))))
