(ns com.wsscode.pathom.viz.electron.background.parser
  (:require ["electron" :refer [app]]
            [com.wsscode.pathom3.connect.built-in.plugins :as pbip]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [com.wsscode.pathom3.connect.operation :as pco]
            [com.wsscode.pathom3.connect.planner :as pcp]
            [com.wsscode.pathom3.interface.async.eql :as p.a.eql]
            [com.wsscode.pathom3.plugin :as p.plugin]
            [promesa.core :as p]))

(pco/defresolver app-version []
  {:pathom.viz.app/version
   (.getVersion app)})

(defonce plan-cache* (atom {}))

(def env
  (-> (pci/register
        [app-version])
      (pcp/with-plan-cache plan-cache*)
      (p.plugin/register
        [pbip/remove-stats-plugin
         (pbip/attribute-errors-plugin)])))

(defn request [tx]
  (p.a.eql/process env tx))

(comment
  (let [tx [:pathom.viz.app/version]]
    (p/let [res (request tx)]
      (js/console.log "!! res" (pr-str res)))))
