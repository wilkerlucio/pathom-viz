(ns com.wsscode.pathom.viz.timeline
  (:require [com.wsscode.pathom3.interface.smart-map :as psm]
            [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [com.wsscode.pathom3.viz.plan :as viz-plan]
            [helix.core :as h]
            [helix.dom :as dom]
            [com.wsscode.pathom3.connect.runner :as pcr]
            [com.wsscode.pathom3.connect.planner :as pcp]
            [com.wsscode.pathom3.connect.operation :as pco]))

(def timeline-env
  (pci/register
    [(pbir/alias-resolver ::pcr/node-run-start-ms ::span-start-ms)
     (pbir/alias-resolver ::pcr/node-run-finish-ms ::span-finish-ms)
     (pbir/alias-resolver ::pcr/node-run-duration-ms ::span-duration-ms)
     (pbir/alias-resolver ::pcr/compute-plan-run-start-ms ::span-start-ms)
     (pbir/alias-resolver ::pcr/compute-plan-run-finish-ms ::span-finish-ms)
     (pbir/alias-resolver ::pcr/compute-plan-run-duration-ms ::span-duration-ms)
     (pbir/alias-resolver ::viz-plan/node-label ::span-label)]))

(defn compute-timeline-tree
  ([data path] (compute-timeline-tree data path nil))
  ([data path start]
   (let [run-stats-plain (some-> data meta ::pcr/run-stats)
         run-stats       (some-> run-stats-plain
                                 (psm/smart-run-stats)
                                 (psm/sm-update-env pci/register
                                   [viz-plan/node-extensions-registry
                                    timeline-env]))
         start           (or start
                             (-> run-stats :com.wsscode.pathom3.connect.runner.stats/process-run-start-ms))]
     (if run-stats
       {:start     (if start
                     (- (:com.wsscode.pathom3.connect.runner.stats/process-run-start-ms run-stats) start)
                     0)
        :name      (str (or (peek path) "Process"))
        :run-stats (volatile! run-stats-plain)
        :path      path
        :details   [{:event    "Make plan"
                     :start    (- (::pcr/compute-plan-run-start-ms run-stats) start)
                     :duration (::pcr/compute-plan-run-duration-ms run-stats)}]
        :duration  (:com.wsscode.pathom3.connect.runner.stats/process-run-duration-ms run-stats)
        :children  (into []
                         (keep
                           (fn [{::pcp/keys      [nested-process node-id]
                                 ::pcr/keys      [node-run-start-ms
                                                  node-run-duration-ms
                                                  resolver-run-start-ms
                                                  resolver-run-duration-ms
                                                  batch-run-start-ms
                                                  batch-run-duration-ms]
                                 ::keys          [span-label]
                                 ::viz-plan/keys [node-output-state]
                                 ::pco/keys      [op-name]
                                 :as             node}]
                             (js/console.log "!! ST" node-output-state)
                             (if node-run-duration-ms
                               (let [path' (conj path node-id)]
                                 (cond-> {:path      path'
                                          :node      (volatile! node)
                                          :run-stats (volatile! run-stats-plain)
                                          :start     (- node-run-start-ms start)
                                          :duration  node-run-duration-ms
                                          :name      (if op-name (str op-name) (str span-label))
                                          :details   (cond-> []
                                                       resolver-run-duration-ms
                                                       (conj
                                                         {:event    "Run resolver"
                                                          :start    (- resolver-run-start-ms start)
                                                          :duration resolver-run-duration-ms
                                                          :style    {:fill
                                                                     (case node-output-state
                                                                       ::viz-plan/node-state-error
                                                                       "#ec6565"

                                                                       "#af9df4")}})

                                                       batch-run-duration-ms
                                                       (conj
                                                         {:event    "Batch run"
                                                          :start    (- batch-run-start-ms start)
                                                          :duration batch-run-duration-ms
                                                          :style    {:fill "#6ac5ec"}}))}
                                   (seq nested-process)
                                   (assoc :children
                                          (into []
                                                (mapcat (fn [attr]
                                                          (let [val (get data attr)]
                                                            (cond
                                                              (map? val)
                                                              (compute-timeline-tree (get data attr)
                                                                [(conj path' attr)]
                                                                start)

                                                              (sequential? val)
                                                              (into []
                                                                    (map-indexed #(compute-timeline-tree %2
                                                                                    (conj path' %)
                                                                                    start))
                                                                    val)

                                                              :else
                                                              []))))
                                                nested-process)))))))
                         (->> run-stats ::pcp/nodes vals (sort-by ::pcr/node-run-start-ms)))}))))
