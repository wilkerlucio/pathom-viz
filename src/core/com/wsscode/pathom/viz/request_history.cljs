(ns com.wsscode.pathom.viz.request-history
  (:require [com.fulcrologic.fulcro.components :as fc]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]))

(fc/defsc RequestItem
  [this {::keys [request response]}]
  {:pre-merge  (fn [{:keys [current-normalized data-tree]}]
                 (merge {::request-id (random-uuid)} current-normalized data-tree))
   :ident      ::request-id
   :query      [::request-id
                ::request
                ::response]
   :css        [[:.container {:border "1px solid #ddd"}]]
   :use-hooks? true}
  (dom/div :.container
    (dom/div (pr-str request))
    (dom/div (pr-str response))))

(def request-item (fc/factory RequestItem {:keyfn ::request-id}))

(fc/defsc RequestHistory
  [this {::keys [requests]}]
  {:pre-merge  (fn [{:keys [current-normalized data-tree]}]
                 (merge {::id (random-uuid)} current-normalized data-tree))
   :ident      ::id
   :query      [::id
                {::requests (fc/get-query RequestItem)}
                {:ui/active-request (fc/get-query RequestItem)}]
   :css        [[:.container {:border         "1px solid #ddd"
                              :display        "flex"
                              :flex-direction "column"
                              :flex           "1"
                              :max-width      "100%"
                              :min-height     "200px"}]]
   :use-hooks? true}
  (dom/div :.container
    (for [req requests]
      (request-item req))))

(def request-history (fc/factory RequestHistory {:keyfn ::id}))
