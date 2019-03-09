(ns com.wsscode.pathom.viz.workspaces
  (:require [cljs.core.async :refer [go <!]]
            [cljs.reader :refer [read-string]]
            [ghostwheel.tracer]
            [com.wsscode.pathom.fulcro.network :as pfn]
            [com.wsscode.pathom.viz.query-editor :as pv.query-editor]
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
                             (pfn/pathom-remote
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
