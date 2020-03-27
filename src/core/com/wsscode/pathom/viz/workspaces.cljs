(ns com.wsscode.pathom.viz.workspaces
  (:require [cljs.core.async :refer [go <!]]
            [cljs.reader :refer [read-string]]
            [com.fulcrologic.fulcro.algorithms.tx-processing :as txn]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [com.wsscode.common.async-cljs :refer [<?maybe]]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.viz.index-explorer :as iex]
            [com.wsscode.pathom.viz.query-editor :as pv.query-editor]
            [edn-query-language.core :as eql]
            [nubank.workspaces.card-types.fulcro3 :as ct.fulcro]
            [nubank.workspaces.model :as wsm]))

(defn pathom-remote [parser]
  {:transmit! (fn transmit! [_ {::txn/keys [ast result-handler]}]
                (let [edn           (eql/ast->query ast)
                      ok-handler    (fn [result]
                                      (try
                                        (result-handler (assoc result :status-code 200))
                                        (catch :default e
                                          (js/console.error e "Result handler for remote failed with an exception."))))
                      error-handler (fn [error-result]
                                      (try
                                        (result-handler (merge error-result {:status-code 500}))
                                        (catch :default e
                                          (js/console.error e "Error handler for remote failed with an exception."))))]
                  (go
                    (try
                      (ok-handler {:body (<?maybe (parser {} edn))})
                      (catch :default e
                        (js/console.error "Pathom Remote error:" e)
                        (error-handler {:body e}))))))})

(defn pathom-card-init
  [card {::keys [parser app load-index-at-start?]
         :or    {load-index-at-start? true}}]
  (let [{:keys [started-callback]} app
        {::wsm/keys       [refresh]
         ::ct.fulcro/keys [app]
         :as              fulcro-card}
        (ct.fulcro/fulcro-card-init
          card
          {::ct.fulcro/root pv.query-editor/QueryEditor
           ::ct.fulcro/app  {#_ #_
                             :client-did-mount
                             (fn [app]
                               (if started-callback
                                 (started-callback app))

                               (if load-index-at-start?
                                 (pv.query-editor/load-indexes app)))

                             :remotes
                             {pv.query-editor/remote-key
                              (pathom-remote
                                (pv.query-editor/client-card-parser parser))}}})]

    (assoc fulcro-card
      ::wsm/refresh
      (fn [node]
        #_
        (pv.query-editor/load-indexes app)
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
        {::ct.fulcro/root
         iex/IndexExplorer

         ::ct.fulcro/app
         {:remotes          {:remote (pathom-remote parser)}
          :client-did-mount (fn [app]
                              (df/load app [::iex/id id] iex/IndexExplorer
                                {:target [:ui/root]}))}}
        portal-options))))

(defn index-explorer-card [config]
  {::wsm/align       ::wsm/stretch-flex
   ::wsm/init        #(index-explorer-card-init % config)
   ::wsm/card-width  7
   ::wsm/card-height 18})
