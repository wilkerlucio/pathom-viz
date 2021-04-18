(ns com.wsscode.pathom.viz.react-hooks-cards
  (:require [nubank.workspaces.card-types.fulcro3 :as ct.fulcro]
            [nubank.workspaces.core :as ws]
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
      (let [[counter set-counter!] (h/use-state 0)]
        (h/use-effect (fn []
                        (js/console.log ">>>> Only In")
                        js/undefined)
          [])
        (h/use-effect (fn []
                        (js/console.log ">>>> Empty Deps")
                        (fn []
                          (js/console.log "<<<< Empty Deps")))
          [])
        (h/use-effect (fn []
                        (js/console.log ">>>> No deps")
                        (fn []
                          (js/console.log "<<<< No Deps"))))
        (h/use-effect (fn []
                        (js/console.log ">>>> Count Dep")
                        (fn []
                          (js/console.log "<<<< Count Dep")))
          [counter])
        (dom/button {:onClick #(set-counter! inc)}
          (str "Click and check console: " counter))))))

(def themes
  {:light {:background "#fff"
           :color      "#000"}
   :dark  {:background "#000"
           :color      "#fff"}})

(def SampleContext (h/create-context (:light themes)))

(fc/defsc ContextDemoThemedButton
  [this _props]
  {:use-hooks? true}
  (let [theme (h/use-context SampleContext)]
    (dom/button {:style theme} (fc/children this))))

(def context-demo-themed-button (fc/factory ContextDemoThemedButton))

(fc/defsc ContextDemoToolbar
  [_this {::keys []}]
  {:use-hooks? true}
  (dom/div
    (context-demo-themed-button {} "Button Content")))

(def context-demo-toolbar (fc/factory ContextDemoToolbar))

(fc/defsc ContextDemoApp
  [_this {::keys []}]
  {:use-hooks? true}
  (h/create-context-provider SampleContext {:value (:dark themes)}
    (context-demo-toolbar {})))

(def context-demo-app (fc/factory ContextDemoApp))

(ws/defcard use-context-card
  (hook-demo-card
    (fn []
      (context-demo-app {}))))

(defn sample-reducer [state {:keys [type]}]
  (case type
    ::increment
    (update state :count inc)

    ::decrement
    (update state :count dec)))

(ws/defcard use-reducer-card
  (hook-demo-card
    (fn []
      (let [[state dispatch] (h/use-reducer sample-reducer {:count 0})]
        (dom/div
          (dom/div "Count: " (:count state))
          (dom/button {:onClick #(dispatch {:type ::decrement})} "-")
          (dom/button {:onClick #(dispatch {:type ::increment})} "+"))))))

(ws/defcard use-callback-card
  (hook-demo-card
    (fn []
      (let [state*   (h/use-atom-state 0)
            callback (h/use-callback #(swap! state* inc))]
        (dom/div
          (dom/div "FN Hash" (hash callback))
          (dom/button {:onClick callback}
            (str "Counter: " @state*)))))))

(ws/defcard use-memo-card
  (hook-demo-card
    (fn []
      (let [state*   (h/use-atom-state 0)
            callback (h/use-memo (fn [] #(swap! state* inc)) [])]
        (dom/div
          (dom/div "FN Hash" (hash callback))
          (dom/button {:onClick callback}
            (str "Counter: " @state*)))))))

(ws/defcard use-ref-card
  (hook-demo-card
    (fn []
      (let [ref (h/use-ref nil)
            cb  (h/use-callback #(.focus (h/ref-current ref)))]
        (dom/div
          (dom/input {:ref ref :type "text"})
          (dom/button {:onClick cb} "Focus the input"))))))

(ws/defcard use-layout-effect-card
  (hook-demo-card
    (fn []
      (let [_ref (h/use-layout-effect (fn []
                                        (js/console.log ">>>> Layout Effect")
                                        (fn []
                                          (js/console.log "<<<< Layout Effect")))
                   [])]
        (dom/div "Check console")))))

(ws/defcard use-debug-value-card
  (hook-demo-card
    (fn []
      (let [[count set-count!] (h/use-state (fn [] 0))]
        (h/use-debug-value (if (< count 5) "Small" "Large"))
        (h/use-debug-value (if (< count 5) "Small" "Large") cljs.core/count)
        (dom/button {:onClick #(set-count! inc)}
          (str "Counter: " count))))))

;; other extensions

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

(ws/defcard use-persistent-state-card
  (hook-demo-card
    (fn []
      (let [count* (h/use-persistent-state ::counter 0)]
        (dom/button {:onClick #(reset! count* (inc @count*))}
          (str "Counter: " @count*))))))
