(ns com.wsscode.pathom.viz.lib.hooks
  (:require [helix.hooks :as hooks]
            [goog.events :as gevents]
            [goog.dom :as gdom]
            [com.wsscode.pathom.viz.lib.local-storage :as ls]
            [garden.core :as garden]
            [com.fulcrologic.fulcro.dom :as dom]))

(deftype ReactFnState [value set-value!]
  IDeref
  (-deref [o] value)

  IFn
  (-invoke [o x] (set-value! x)))

(defn use-fstate [initial-value]
  (let [[value set-value!] (hooks/use-state initial-value)]
    (->ReactFnState value set-value!)))

(defn use-persistent-state [store-key initial-value]
  (let [[value set-value!] (hooks/use-state (ls/get store-key initial-value))
        set-persistent! (fn [x]
                          (ls/set! store-key x)
                          (doto x set-value!))]
    (->ReactFnState value set-persistent!)))

(defn use-event-listener
  ([element event-name handler]
   (let [handler* (hooks/use-ref nil)]
     (hooks/use-effect [handler]
       (reset! handler* handler))

     (hooks/use-effect [element event-name]
       (let [listener (fn [e]
                        (if-let [f @handler*]
                          (f e)))]
         (.addEventListener element event-name listener)
         #(.removeEventListener element event-name listener))))))

(defn create-style-element [css]
  (doto (js/document.createElement "style")
    (gdom/appendChild (js/document.createTextNode css))))

(defn inject-css [css]
  (let [style (create-style-element css)]
    (gdom/appendChild js/document.head style)
    #(gdom/removeNode style)))

(defn use-garden-css [styles]
  (let [css    (hooks/use-memo [(hash styles)] (garden/css styles))
        ready? (use-fstate false)]
    (hooks/use-effect [(hash styles)]
      (let [remove (inject-css css)]
        (ready? true)
        remove))

    (if-not @ready?
      (dom/noscript))))
