(ns com.wsscode.pathom.viz.request-history
  (:require [com.fulcrologic.fulcro.components :as fc]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.wsscode.pathom.viz.ui.kit :as ui]
            [com.wsscode.pathom.viz.helpers :as pvh]
            [com.fulcrologic.fulcro.mutations :as fm]
            [com.wsscode.pathom.viz.codemirror :as cm]
            [com.wsscode.pathom.viz.trace-with-plan :as trace+plan]
            [com.wsscode.pathom.viz.codemirror6 :as cm6]))

(defn pre-merge-request [{:keys [current-normalized data-tree]}]
  (let [id        (or (::request-id data-tree)
                      (::request-id current-normalized)
                      (random-uuid))
        trace     (pvh/response-trace (::response data-tree))
        data-tree (cond-> data-tree
                    trace
                    (assoc ::trace-viewer
                           {:com.wsscode.pathom/trace trace}))]
    (merge {::request-id id}
      current-normalized data-tree)))

(fc/defsc RequestView
  [this {::keys [request response trace-viewer]}]
  {:pre-merge  pre-merge-request
   :ident      ::request-id
   :query      [::request-id
                ::request
                ::response
                :ui/graph-view
                ::trace-viewer]
   :css        [[:.header {:background    "#f7f7f7"
                           :border-bottom "1px solid #ddd"
                           :padding       "4px 8px"}
                 ui/text-sans-13]
                [:.trace {:display  "flex"
                          :overflow "hidden"}]]
   :use-hooks? true}
  (let [response-size (pvh/use-persistent-state ::response-size 400)
        trace-size    (pvh/use-persistent-state ::trace-size 300)]
    (ui/column (ui/gc :.flex)
      (ui/row (ui/gc :.flex)
        (ui/column (ui/gc :.flex)
          (dom/div :.header "Request")
          (cm/clojure {::cm/options {::cm/readOnly true}
                       :style       {:flex     "1"
                                     :overflow "auto"
                                     :position "relative"}
                       :value       (pvh/pprint-str request)}))
        (ui/drag-resize {:state response-size :direction "right"})
        (ui/column {:style {:width (str @response-size "px")}}
          (dom/div :.header "Response")
          (cm6/clojure-read (dissoc response :com.wsscode.pathom/trace))))
      (if trace-viewer
        (fc/fragment
          (ui/drag-resize {:state trace-size :direction "down"})
          (dom/div :.header "Trace")
          (dom/div :.trace {:style {:height (str @trace-size "px")}}
            (trace+plan/trace-with-plan (:com.wsscode.pathom/trace trace-viewer)
              {:on-log-snaps
               (fn [snaps]
                 (fc/transact! this [(list 'com.wsscode.pathom.viz.electron.renderer.main/log-new-entry
                                       {:entry {:pathom.viz.log/type :pathom.viz.log.type/plan-snapshots
                                                :pathom.viz.log/data snaps}})]))})))))))

(def request-view (fc/factory RequestView {:keyfn ::request-id}))

(fc/defsc RequestItem
  [_this {::keys [request-id request]} {::keys [on-select selected?]}]
  {:pre-merge pre-merge-request
   :ident     ::request-id
   :query     [::request-id
               ::request
               ::trace-viewer]
   :css       [[:.outer
                {:border-right "12px solid #eee"}]
               [:.container
                {:border-bottom "1px solid #ccc"
                 :cursor        "pointer"
                 :font-family   ui/font-code
                 :max-height    "104px"
                 :overflow      "auto"
                 :padding       "5px"
                 :white-space   "pre"}
                [:&:hover {:background ui/color-highlight
                           :color      "#000"}]
                [:&.selected {:background "#cae9fb"}]]
               [:.code {:white-space "pre"}]]}
  (dom/div :.outer
    (dom/div :.container {:classes [(if selected? :.selected)] :onClick #(on-select request-id)}
      (pvh/pprint-str request))))

(def request-item (fc/computed-factory RequestItem {:keyfn ::request-id}))

#_:clj-kondo/ignore
(fm/defmutation clear-history [_]
  (action [{:keys [state ref]}]
    (swap! state update-in ref assoc
      ::requests []
      :ui/active-request nil)))

(fc/defsc RequestHistory
  [this {::keys   [requests]
         :ui/keys [active-request]}]
  {:pre-merge   (fn [{:keys [current-normalized data-tree]}]
                  (merge {::id (random-uuid)} current-normalized data-tree))
   :ident       ::id
   :query       [::id
                 {::requests (fc/get-query RequestItem)}
                 {:ui/active-request (fc/get-query RequestView)}]
   :css         [[:.container {:border         "1px solid #ddd"
                               :display        "flex"
                               :flex-direction "column"
                               :flex           "1"
                               :max-width      "100%"}]
                 [:.blank {:background      "#ccc"
                           :flex            "1"
                           :display         "flex"
                           :align-items     "center"
                           :justify-content "center"}
                  ui/text-sans-13]]
   :css-include [RequestItem ui/UIKit]
   :use-hooks?  true}
  (let [select-item  (pvh/use-callback #(fm/set-value! this :ui/active-request [::request-id %]))
        request-size (pvh/use-persistent-state ::request-size 300)]
    (ui/column {:classes [(ui/component-class RequestHistory :.container)]}
      (if (seq requests)
        (fc/fragment
          (ui/toolbar {}
            (dom/div (ui/gc :.flex))
            (dom/button {:onClick #(fc/transact! this [(clear-history {})])} "Clear"))

          (dom/div (ui/gc :.flex :.scrollbars)
            (for [req (rseq requests)]
              (request-item req
                {::on-select select-item
                 ::selected? (= (::request-id req) (::request-id active-request))})))

          (if active-request
            (fc/fragment
              (ui/drag-resize {:state request-size :direction "down"})
              (ui/column {:classes [(ui/css :.scrollbars)]
                          :style   {:height (str @request-size "px")}}
                (request-view active-request)))))
        (dom/div :.blank "No request tracked yet.")))))

(def request-history (fc/factory RequestHistory {:keyfn ::id}))
