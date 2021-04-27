(ns com.wsscode.pathom3.viz.planner-explorer
  (:require-macros [com.wsscode.pathom.viz.embed.macros :refer [defc]])
  (:require [com.fulcrologic.fulcro.dom :as dom]
            [com.wsscode.pathom.viz.lib.hooks :as p.hooks]
            [com.wsscode.pathom3.viz.plan :as viz-plan]
            [helix.hooks :as hooks]
            [com.wsscode.pathom.viz.helpers :as h]
            [helix.core :refer [$]]
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

(defn use-debounced [value delay]
  (let [[debounced set-debounced] (hooks/use-state value)]
    (hooks/use-effect (hash-compare [value])
      (let [timer (js/setTimeout #(set-debounced value) delay)]
        #(js/clearTimeout timer)))

    debounced))

(defn use-debounced-memo [deps delay f]
  (hooks/use-memo* f [(use-debounced deps delay)]))

(defn fstate-input [!state]
  {:value    @!state
   :onChange #(!state (.. % -target -value))})

(defc planner-explorer
  [{::pci/keys                    [index-oir]
    :edn-query-language.core/keys [query]}]
  (let [!indexes (p.hooks/use-fstate (or (some-> index-oir pr-str) "{}"))
        !query   (p.hooks/use-fstate (or (some-> query pr-str) "[]"))
        frames   (use-debounced-memo [@!indexes @!query] 500
                   (fn []
                     (let [idx   (h/safe-read-string @!indexes)
                           query (h/safe-read-string @!query)]
                       (->> {::pci/index-oir                idx
                             :edn-query-language.core/query query}
                            (viz-plan/compute-frames)))))]
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
