(ns com.wsscode.pathom.viz.workspaces
  (:require [cljs.core.async :refer [go <!]]
            [cljs.reader :refer [read-string]]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.fulcro.network :as p.network]
            [com.wsscode.pathom.viz.index-explorer :as iex]
            [com.wsscode.pathom.viz.query-editor :as pv.query-editor]
            [fulcro.client.data-fetch :as df]
            [nubank.workspaces.card-types.fulcro :as ct.fulcro]
            [nubank.workspaces.lib.fulcro-portal :as f.portal]
            [nubank.workspaces.model :as wsm]))

(defn pathom-card-init
  [card {::keys [parser app load-index-at-start?]
         :or    {load-index-at-start? true}}]
  (let [{:keys [started-callback]} app
        {::wsm/keys       [refresh]
         ::ct.fulcro/keys [app*]
         :as              fulcro-card}
        (ct.fulcro/fulcro-card-init
          card
          {::f.portal/root pv.query-editor/QueryEditor
           ::f.portal/app  {:started-callback
                            (fn [app]
                              (if started-callback
                                (started-callback app))

                              (if load-index-at-start?
                                (pv.query-editor/load-indexes app)))

                            :networking
                            {pv.query-editor/remote-key
                             (p.network/pathom-remote
                               (pv.query-editor/client-card-parser parser))}}})]
    (assoc fulcro-card
      ::wsm/refresh
      (fn [node]
        (pv.query-editor/load-indexes @app*)
        (refresh node)))))

(defn pathom-card [config]
  {::wsm/align       {:flex    1
                      :display "flex"}
   ::wsm/init        #(pathom-card-init % config)
   ::wsm/card-width  7
   ::wsm/card-height 18})

(defn index-explorer-card-init [card {::p/keys [parser]
                                      ::keys   [portal-options]}]
  (let [id "singleton"]
    (ct.fulcro/fulcro-card-init card
      (merge
        {::f.portal/root
         iex/IndexExplorer

         ::f.portal/app
         {:networking       (-> (p.network/pathom-remote parser)
                                (p.network/trace-remote))
          :started-callback (fn [app]
                              (df/load app [::iex/id id] iex/IndexExplorer
                                {:target [:ui/root]}))}}
        portal-options))))

(defn index-explorer-card [config]
  {::wsm/align       ::wsm/stretch-flex
   ::wsm/init        #(index-explorer-card-init % config)
   ::wsm/card-width  7
   ::wsm/card-height 18})
