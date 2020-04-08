(ns com.wsscode.pathom.viz.request-history
  (:require [com.fulcrologic.fulcro.components :as fc]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]))

(fc/defsc RequestItem
  [this {::keys []}]
  {:pre-merge  (fn [{:keys [current-normalized data-tree]}]
                 (merge {::request-id (random-uuid)} current-normalized data-tree))
   :ident      ::request-id
   :query      [::request-id
                ::request
                ::response]
   :use-hooks? true}
  (dom/div))

(def request-item (fc/factory RequestItem {:keyfn ::request-id}))

(fc/defsc RequestHistory
  [this {::keys []}]
  {:pre-merge  (fn [{:keys [current-normalized data-tree]}]
                 (merge {::id (random-uuid)} current-normalized data-tree))
   :ident      ::id
   :query      [::id
                {::requests (fc/get-query RequestItem)}
                {:ui/active-request (fc/get-query RequestItem)}]
   :use-hooks? true}
  (dom/div))

(def request-history (fc/factory RequestHistory {:keyfn ::id}))
