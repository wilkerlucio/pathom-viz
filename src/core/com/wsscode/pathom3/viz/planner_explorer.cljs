(ns com.wsscode.pathom3.viz.planner-explorer
  (:require-macros [com.wsscode.pathom.viz.embed.macros :refer [defc]])
  (:require [com.fulcrologic.fulcro.dom :as dom]
            [com.wsscode.pathom.viz.codemirror6 :as cm]
            [com.wsscode.pathom.viz.lib.hooks :as p.hooks]
            [com.wsscode.pathom3.viz.plan :as viz-plan]
            [helix.hooks :as hooks]
            [com.wsscode.pathom.viz.helpers :as h]
            [helix.core :refer [$]]
            [goog.functions :as gfun]
            [com.wsscode.pathom3.connect.indexes :as pci]))

(defn use-custom-compare-memoize
  [deps deps-are-equal?]
  (let [ref (hooks/use-ref js/undefined)]
    (if (or (not @ref)
            (not (deps-are-equal? @ref deps)))
      (reset! ref deps))

    @ref))

(defn hash-compare [deps]
  (use-custom-compare-memoize deps #(= (hash %) (hash %2))))

(defn fstate-input [!state]
  {:value    @!state
   :onChange #(!state (.. % -target -value))})

(defn use-debounced [value delay]
  (let [[debounced set-debounced] (hooks/use-state value)]
    (hooks/use-effect [(hash value)]
      (let [timer (js/setTimeout #(set-debounced value) delay)]
        #(js/clearTimeout timer)))

    debounced))

(defn use-debounced-memo [deps interval f]
  (let [!v  (p.hooks/use-fstate (f))
        dbv (hooks/use-callback []
              (gfun/debounce #(!v (f)) interval))]
    (hooks/use-effect* dbv deps)
    @!v))

(defc planner-explorer [{:keys [index-oir query]}]
  (let [!indexes (p.hooks/use-fstate (or index-oir "{}"))
        !query   (p.hooks/use-fstate (or query "[]"))
        db       (use-debounced [@!indexes @!query] 500)
        frames   (hooks/use-memo [(hash db)]
                   (let [idx   (h/safe-read-string (first db))
                         query (h/safe-read-string (second db))]
                     (->> {::pci/index-oir                idx
                           :edn-query-language.core/query query}
                          (viz-plan/compute-frames)
                          (mapv (juxt identity viz-plan/compute-plan-elements)))))]
    (dom/div {:classes ["flex-col flex-1 overflow-hidden"]}
      (dom/div {:classes ["flex-row"]}
        (dom/textarea (merge {:classes ["flex-1 h-60"]}
                        (fstate-input !indexes)))
        (dom/textarea (merge {:classes ["flex-1 h-60"]}
                        (fstate-input !query))))
      (dom/div {:classes ["flex-1"]}
        (if frames
          ($ viz-plan/PlanSnapshots
            {:frames
             frames

             :display
             ::viz-plan/display-type-node-id})
          "Invalid index or query.")))))
