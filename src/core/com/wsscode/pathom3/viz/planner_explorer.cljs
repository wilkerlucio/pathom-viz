(ns com.wsscode.pathom3.viz.planner-explorer
  (:require-macros [com.wsscode.pathom.viz.embed.macros :refer [defc]])
  (:require [com.fulcrologic.fulcro.dom :as dom]
            [com.wsscode.pathom.viz.codemirror6 :as cm]
            [com.wsscode.pathom.viz.lib.hooks :as p.hooks]
            [com.wsscode.pathom3.viz.plan :as viz-plan]
            [helix.hooks :as hooks]
            [com.wsscode.pathom.viz.helpers :as h]
            [helix.core :refer [$]]
            [com.wsscode.pathom3.connect.indexes :as pci]))

(defn fstate-dom-hookup [!state]
  {:value    @!state
   :onChange #(!state (.. % -target -value))})

(defn use-parsed [!x f]
  (hooks/use-memo [(hash @!x)] (f @!x)))

(defc planner-explorer [{:keys [index-oir query]}]
  (let [!indexes (p.hooks/use-fstate (or index-oir "{}"))
        !query   (p.hooks/use-fstate (or query "[]"))
        idx      (use-parsed !indexes h/safe-read-string)
        query    (use-parsed !query h/safe-read-string)]
    (dom/div {:classes ["flex-col flex-1"]}
      (dom/div {:classes ["flex-row"]}
        (dom/textarea (merge {:classes ["flex-1 h-60"]}
                        (fstate-dom-hookup !indexes)))
        (dom/textarea (merge {:classes ["flex-1 h-60"]}
                        (fstate-dom-hookup !query))))
      (dom/div {:classes ["flex-1"]}
        (if (and idx query)
          ($ viz-plan/PlanSnapshots
            {:frames
             (->> (viz-plan/compute-frames
                    {::pci/index-oir                idx
                     :edn-query-language.core/query query})
                  (mapv (juxt identity viz-plan/compute-plan-elements)))

             :display
             ::viz-plan/display-type-node-id})
          "Invalid index or query.")))))
