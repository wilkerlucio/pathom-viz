(ns com.wsscode.pathom.viz.embed.fulcro-float-roots
  (:require [com.fulcrologic.fulcro.components :as fc]
            [com.fulcrologic.fulcro.rendering.multiple-roots-renderer :as mroot]
            [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.application :as app]))

(defonce app (app/fulcro-app {:optimized-render! mroot/render!}))

(fc/defsc Child [this props]
  {:query [:id :n :bar]
   :ident :id}
  (dom/div "Child"))

(def child (fc/factory Child {:keyfn :id}))

(fc/defsc AltRoot [this props]
  {:query                [{:alt-child (fc/get-query Child)}]
   :componentDidMount    (fn [this] (mroot/register-root! this {:app app}))
   :componentWillUnmount (fn [this] (mroot/deregister-root! this {:app app}))
   :initial-state        {:alt-child [{:id 1 :n 22}
                                      {:id 2 :n 44}]}}
  (dom/div
    (mapv child (:alt-child props))))

(def alt-root (mroot/floating-root-factory AltRoot))
