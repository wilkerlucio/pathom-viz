(ns com.wsscode.pathom.viz.timeline
  (:require [clojure.set :as set]
            [com.wsscode.pathom3.connect.built-in.plugins :as pbip]
            [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [com.wsscode.pathom3.connect.operation :as pco]
            [com.wsscode.pathom3.connect.planner :as pcp]
            [com.wsscode.pathom3.connect.runner :as pcr]
            [com.wsscode.pathom3.connect.runner.stats :as pcrs]
            [com.wsscode.pathom3.interface.eql :as p.eql]
            [com.wsscode.pathom3.interface.smart-map :as psm]
            [com.wsscode.pathom3.plugin :as p.plugin]
            [com.wsscode.pathom3.viz.plan :as viz-plan]
            [com.wsscode.misc.coll :as coll]))

(def timeline-env
  (pci/register
    [(pbir/alias-resolver ::pcr/compute-plan-run-duration-ms ::span-duration-ms)
     (pbir/alias-resolver ::pcr/compute-plan-run-finish-ms ::span-finish-ms)
     (pbir/alias-resolver ::pcr/compute-plan-run-start-ms ::span-start-ms)
     (pbir/alias-resolver ::pcr/node-run-duration-ms ::span-duration-ms)
     (pbir/alias-resolver ::pcr/node-run-finish-ms ::span-finish-ms)
     (pbir/alias-resolver ::pcr/node-run-start-ms ::span-start-ms)
     (pbir/alias-resolver ::pcr/process-run-duration-ms ::pcrs/process-run-duration-ms)
     (pbir/alias-resolver ::pcr/process-run-finish-ms ::pcrs/process-run-finish-ms)
     (pbir/alias-resolver ::pcr/process-run-start-ms ::pcrs/process-run-start-ms)
     (pbir/alias-resolver ::viz-plan/node-label ::span-label)]))

(def plan-cache* (atom {}))

(def stats-env-base
  (-> (p.plugin/register pbip/remove-stats-plugin)
      (pcp/with-plan-cache plan-cache*)
      (pci/register
        [pcrs/stats-registry
         viz-plan/node-extensions-registry
         timeline-env])))

(defn stats-env [stats]
  (pci/register stats stats-env-base))

(declare compute-timeline-tree)

(defn compute-children-trace [data path start attr]
  (let [val (get data attr)]
    (cond
      (map? val)
      [(compute-timeline-tree (get data attr)
         path
         start)]

      (sequential? val)
      (let [children
                  (into []
                        (map-indexed #(compute-timeline-tree %2
                                        (conj path %)
                                        start))
                        val)

            start (:start (first (sort-by :start children)))
            end   (->> children
                       (mapv #(+ (:start %) (:duration %)))
                       (sort #(compare %2 %))
                       first)
            duration (- end start)]
        [{:start     start
          :duration  duration
          :name      (str (peek path))
          :path      path
          :details   []
          :children  children}])

      :else
      [])))

(defn merge-sub-timeline [entry sub-entry]
  (if sub-entry
    (-> entry
        (assoc :run-stats (:run-stats sub-entry))
        (update :details into (:details sub-entry))
        (update :children into (:children sub-entry))
        (update :children #(->> % (sort-by :start) vec)))
    entry))

(defn compute-nested-data-children
  [data path start]
  (let [nested-process (keys (coll/filter-vals
                               (fn [x]
                                 (cond
                                   (map? x)
                                   (-> x meta (contains? ::pcr/run-stats))

                                   (coll? x)
                                   (-> x first meta (contains? ::pcr/run-stats))))
                               data))]
    (into []
          (mapcat (fn [attr]
                    (compute-children-trace data (conj path attr) start attr)))
          nested-process)))

(defn compute-mutation-children
  [data path start {::pcp/keys [mutations]} run-stats]
  (into []
        (keep
          (fn [{:keys [key]}]
            (let [path'        (conj path key)
                  {::pcr/keys [node-run-start-ms
                               node-run-duration-ms
                               mutation-run-start-ms
                               mutation-run-duration-ms
                               ]} (get-in run-stats [::pcr/node-run-stats key])

                  response     (get data key)
                  error?       (contains? response ::pcr/mutation-error)
                  sub-timeline (cond
                                 error?
                                 nil

                                 (map? response)
                                 (compute-timeline-tree response path' start))]
              (-> {:path      path'
                   :run-stats (volatile! nil)
                   :start     (- node-run-start-ms start)
                   :duration  node-run-duration-ms
                   :name      (str key)
                   :details   [{:start    (- mutation-run-start-ms start)
                                :duration mutation-run-duration-ms
                                :event    "Call mutation"
                                :style    {:fill
                                           (if error?
                                             "#ec6565"
                                             "#f49def")}}]
                   :children  []}
                  (merge-sub-timeline sub-timeline)))))
        mutations))

(defn compute-nodes-children
  [path
   start
   run-stats-plain
   {:keys [nodes]}]
  (into []
        (keep
          (fn [{::pco/keys      [op-name]
                ::pcp/keys      [node-id]
                ::pcr/keys      [batch-run-duration-ms
                                 batch-run-start-ms
                                 node-run-duration-ms
                                 node-run-start-ms
                                 resolver-run-duration-ms
                                 resolver-run-start-ms]
                ::keys          [span-label]
                ::viz-plan/keys [node-output-state]
                :as             node}]
            (if node-run-duration-ms
              (let [path' (conj path node-id)]
                {:path      path'
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
                                 :style    {:fill "#6ac5ec"}}))}))))
        nodes))

(defn compute-timeline-tree
  ([data] (compute-timeline-tree data []))
  ([data path] (compute-timeline-tree data path nil))
  ([data path start]
   (when-let [run-stats-plain (some-> data meta ::pcr/run-stats)]
     (let [run-stats (p.eql/process (stats-env run-stats-plain)
                       (assoc run-stats-plain
                         :nodes (vals (::pcp/nodes run-stats-plain)))
                       [{:nodes
                         [::pco/op-name
                          ::pcp/nested-process
                          ::pcp/node-id
                          ::pcr/batch-run-duration-ms
                          ::pcr/batch-run-start-ms
                          ::pcr/node-run-duration-ms
                          ::pcr/node-run-start-ms
                          ::pcr/resolver-run-duration-ms
                          ::pcr/resolver-run-start-ms
                          ::span-label
                          ::viz-plan/node-output-state]}
                        {::pcr/node-run-stats
                         [::pcr/node-run-duration-ms
                          ::pcr/mutation-run-duration-ms
                          '*]}
                        ::pcrs/process-run-start-ms
                        ::pcrs/process-run-duration-ms
                        ::pcr/compute-plan-run-start-ms
                        ::pcr/compute-plan-run-duration-ms
                        ::pcp/nested-process])
           start     (or start
                         (::pcrs/process-run-start-ms run-stats))]
       {:start     (if start
                     (- (::pcrs/process-run-start-ms run-stats) start)
                     0)
        :hint      (str (or (peek path) "Process"))
        :name      (str (peek path))
        :run-stats (volatile! run-stats-plain)
        :path      path
        :details   [{:event    "Make plan"
                     :start    (- (::pcr/compute-plan-run-start-ms run-stats) start)
                     :duration (::pcr/compute-plan-run-duration-ms run-stats)}]
        :duration  (::pcrs/process-run-duration-ms run-stats)
        :children  (->> (into []
                              (concat
                                (compute-mutation-children data path start run-stats-plain run-stats)
                                (compute-nested-data-children data path start)
                                (compute-nodes-children path start run-stats-plain run-stats)))
                        (sort-by :start)
                        vec)}))))
