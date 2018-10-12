(ns com.wsscode.pathom.viz.codemirror-cards
  (:require [cljs.core.async :as async]
            [com.wsscode.common.async-cljs :refer [<? go-catch]]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.fulcro.network :as pfn]
            [com.wsscode.pathom.viz.codemirror :as cm]
            [nubank.workspaces.card-types.fulcro :as ct.fulcro]
            [nubank.workspaces.card-types.react :as ct.react]
            [nubank.workspaces.core :as ws]
            [nubank.workspaces.lib.fulcro-portal :as f.portal]
            [nubank.workspaces.model :as wsm]
            [fulcro.client.primitives :as fp]
            [fulcro.client.localized-dom :as dom]))

(fp/defsc PathomCard
  [this {::keys []}]
  {:initial-state (fn [_]
                    {::id (random-uuid)})
   :ident         [::id ::id]
   :query         [::id ::pc/indexes]
   :css           [[:.container {:display "flex"}]]}
  (dom/div :.container
    (cm/pathom {})))

(defn pathom-card-init
  [{::wsm/keys [card-id]
    :as        card}

   {::keys [parser]}]
  (let [{::wsm/keys [refresh] :as fulcro-card}
        (ct.fulcro/fulcro-card-init
          card
          {::f.portal/root PathomCard
           ::f.portal/app  {:started-callback
                            (fn [app]
                              (js/console.log "Mounted" app))

                            :remote
                            (pfn/pathom-remote parser)}})]
    (assoc fulcro-card
      ::wsm/refresh
      (fn [node]
        ; TODO refresh index
        (refresh node)))))

(defn pathom-card [config]
  {::wsm/init
   #(pathom-card-init % config)})

(def indexes (atom {}))

(defmulti resolver-fn pc/resolver-dispatch)
(def defresolver (pc/resolver-factory resolver-fn indexes))

(defmulti mutation-fn pc/mutation-dispatch)
(def defmutation (pc/mutation-factory mutation-fn indexes))

(def parser
  (p/parallel-parser {::p/env     (fn [env]
                                    (merge
                                      {::p/reader             [p/map-reader pc/parallel-reader pc/ident-reader pc/index-reader]
                                       ::pc/resolver-dispatch resolver-fn
                                       ::pc/indexes           @indexes}
                                      env))
                      ::p/mutate  pc/mutate-async
                      ::p/plugins [p/error-handler-plugin
                                   p/request-cache-plugin
                                   p/trace-plugin]}))

(ws/defcard conj-demo-card
  {::wsm/align {:flex 1}}
  (pathom-card {::parser parser}))
