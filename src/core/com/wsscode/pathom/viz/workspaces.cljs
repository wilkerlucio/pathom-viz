(ns com.wsscode.pathom.viz.workspaces
  (:require [cljs.core.async :refer [go <!]]
            [cljs.reader :refer [read-string]]
            [com.wsscode.pathom.fulcro.network :as pfn]
            [com.wsscode.pathom.viz.query-editor :as pv.query-editor]
            [nubank.workspaces.card-types.fulcro :as ct.fulcro]
            [nubank.workspaces.lib.fulcro-portal :as f.portal]
            [nubank.workspaces.model :as wsm]))

(defn pathom-card-init
  [card {::keys [parser]}]
  (let [{::wsm/keys       [refresh]
         ::ct.fulcro/keys [app*]
         :as              fulcro-card}
        (ct.fulcro/fulcro-card-init
          card
          {::f.portal/root pv.query-editor/QueryEditor
           ::f.portal/app  {:started-callback
                            pv.query-editor/load-indexes

                            :networking
                            {pv.query-editor/remote-key
                             (pfn/pathom-remote
                               (pv.query-editor/client-card-parser parser))}}})]
    (assoc fulcro-card
      ::wsm/refresh
      (fn [node]
        (pv.query-editor/load-indexes @app*)
        (refresh node)))))

(defn pathom-card [config]
  {::wsm/init #(pathom-card-init % config)

   ::wsm/align {:flex    1
                :display "flex"}})
