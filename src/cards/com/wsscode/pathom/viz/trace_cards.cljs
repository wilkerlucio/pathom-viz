(ns com.wsscode.pathom.viz.trace-cards
  (:require [com.wsscode.pathom.viz.trace :as trace]
            [nubank.workspaces.card-types.fulcro :as ct.fulcro]
            [nubank.workspaces.card-types.react :as ct.react]
            [nubank.workspaces.core :as ws]
            [nubank.workspaces.model :as wsm]
            [nubank.workspaces.lib.fulcro-portal :as f.portal]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.common.async-cljs :refer [<? go-catch]]
            [cljs.core.async :as async]
            [com.wsscode.pathom.core :as p]))

(def indexes (atom {}))

(defmulti resolver-fn pc/resolver-dispatch)
(def defresolver (pc/resolver-factory resolver-fn indexes))

(defmulti mutation-fn pc/mutation-dispatch)
(def defmutation (pc/mutation-factory mutation-fn indexes))

(def color-map
  {1 "red"
   2 "green"
   3 "blue"})

(defresolver `color
  {::pc/input  #{::id}
   ::pc/output [::color]
   ::pc/batch? true}
  (pc/batch-resolver
    (fn [env {::keys [id]}]
      (go-catch
        (<? (async/timeout 300))
        {::color
         (get color-map id "black")}))
    (fn [env ids]
      (go-catch
        (<? (async/timeout 300))
        (mapv
          #(hash-map ::color (get color-map (::id %) "black"))
          ids)))))

(defresolver `weight
  {::pc/input  #{::id}
   ::pc/output [::weight ::size]}
  (fn [env {::keys [id]}]
    (go-catch
      (<? (async/timeout 100))
      {::weight
       (case id
         1 30
         2 80
         3 200
         0)

       ::size
       (case id
         1 1
         2 3
         3 9
         0)})))

(defresolver `rel
  {::pc/input  #{::id}
   ::pc/output [::relation]}
  (fn [env {::keys [id]}]
    (go-catch
      (<? (async/timeout 50))
      {::relation
       {::id
        (case id
          1 2
          2 3
          3 1
          1)}})))

(defresolver `all
  {::pc/output [{::all [::id]}]}
  (fn [env _]
    {::all [{::id 1} {::id 2} {::id 3} {::id 2}]}))

(defresolver `error
  {::pc/input  #{}
   ::pc/output [::error]}
  (fn [env {::keys [id]}]
    (throw (ex-info "Error" {:ex "data"}))))

(defresolver `darken-color
  {::pc/input  #{::color}
   ::pc/output [::color-darken]}
  (fn [env {::keys [color]}]
    (go-catch
      (<? (async/timeout 20))
      {::color-darken (str color "-darken")})))

(defresolver `lighter-color
  {::pc/input  #{::color}
   ::pc/output [::color-lighter]}
  (fn [env {::keys [color]}]
    (go-catch
      (<? (async/timeout 50))
      {::color-lighter (str color "-lighter")})))

(def demo-parser
  (p/parallel-parser {::p/env     {::p/reader               [p/map-reader pc/all-parallel-readers
                                                             p/env-placeholder-reader]
                                   ::pc/resolver-dispatch   resolver-fn
                                   ::pc/mutate-dispatch     mutation-fn
                                   ::pc/indexes             @indexes
                                   ::p/placeholder-prefixes #{">"}}
                      ::p/mutate  pc/mutate-async
                      ::p/plugins [p/error-handler-plugin
                                   p/request-cache-plugin
                                   p/trace-plugin]}))

(def sample-trace
  '{:start    0,
    :path     [],
    :duration 366,
    :details  [{:event "process-pending", :duration 0, :start 366, :provides #{[:dev.playground/id 1]}, :merge-result? true}],
    :children [{:start    1,
                :path     [[:dev.playground/id 1]],
                :duration 365,
                :details  [{:event "compute-plan", :duration 0, :start 1}
                           {:event "call-read", :duration 0, :start 1}
                           {:event "async-return", :duration 0, :start 1}
                           {:event         "process-pending",
                            :duration      0,
                            :start         109,
                            :provides      #{:dev.playground/size :dev.playground/weight},
                            :merge-result? false}
                           {:event     "reset-loop",
                            :duration  0,
                            :start     110,
                            :loop-keys [:dev.playground/size :dev.playground/weight]}
                           {:event         "process-pending",
                            :duration      0,
                            :start         305,
                            :provides      #{:dev.playground/color},
                            :merge-result? false}
                           {:event "reset-loop", :duration 0, :start 305, :loop-keys [:dev.playground/color]}
                           {:event         "process-pending",
                            :duration      0,
                            :start         330,
                            :provides      #{:dev.playground/color-darken},
                            :merge-result? false}
                           {:event "reset-loop", :duration 0, :start 330, :loop-keys [:dev.playground/color-darken]}
                           {:event         "process-pending",
                            :duration      0,
                            :start         362,
                            :provides      #{:dev.playground/color-lighter},
                            :merge-result? false}
                           {:event "reset-loop", :duration 0, :start 362, :loop-keys [:dev.playground/color-lighter]}
                           {:event "merge-result", :duration 0, :start 366}],
                :name     "[:dev.playground/id 1]",
                :children [{:start    1,
                            :path     [[:dev.playground/id 1] :dev.playground/color-lighter],
                            :duration 364,
                            :details  [{:event    "compute-plan",
                                        :duration 0,
                                        :start    1,
                                        :plan     (([:dev.playground/color dev.playground/color]
                                                     [:dev.playground/color-lighter dev.playground/lighter-color]))}
                                       {:event      "call-resolver-with-cache",
                                        :duration   0,
                                        :start      1,
                                        :input-data #:dev.playground{:id 1},
                                        :sym        dev.playground/color,
                                        :key        :dev.playground/color-lighter}
                                       {:event "call-read", :duration 0, :start 1}
                                       {:event      "schedule-resolver",
                                        :duration   0,
                                        :start      2,
                                        :input-data #:dev.playground{:id 1},
                                        :sym        dev.playground/color,
                                        :key        :dev.playground/color-lighter}
                                       {:event      "call-resolver",
                                        :duration   303,
                                        :start      2,
                                        :input-data #:dev.playground{:id 1},
                                        :sym        dev.playground/color,
                                        :key        :dev.playground/color-lighter}
                                       {:event    "merge-resolver-response",
                                        :duration 0,
                                        :start    305,
                                        :sym      dev.playground/color,
                                        :key      :dev.playground/color-lighter}
                                       {:event      "call-resolver-with-cache",
                                        :duration   0,
                                        :start      305,
                                        :input-data #:dev.playground{:color "red"},
                                        :sym        dev.playground/lighter-color,
                                        :key        :dev.playground/color-lighter}
                                       {:event      "schedule-resolver",
                                        :duration   0,
                                        :start      308,
                                        :input-data #:dev.playground{:color "red"},
                                        :sym        dev.playground/lighter-color,
                                        :key        :dev.playground/color-lighter}
                                       {:event      "call-resolver",
                                        :duration   53,
                                        :start      308,
                                        :input-data #:dev.playground{:color "red"},
                                        :sym        dev.playground/lighter-color,
                                        :key        :dev.playground/color-lighter}
                                       {:event    "merge-resolver-response",
                                        :duration 0,
                                        :start    362,
                                        :sym      dev.playground/lighter-color,
                                        :key      :dev.playground/color-lighter}
                                       {:event "call-read", :duration 0, :start 362}
                                       {:event "value-return", :duration 0, :start 365}],
                            :name     ":dev.playground/color-lighter"}
                           {:start    3,
                            :path     [[:dev.playground/id 1] :dev.playground/color],
                            :duration 303,
                            :details  [{:event "skip-wait-key", :duration 0, :start 3}
                                       {:event "call-read", :duration 0, :start 305}
                                       {:event "value-return", :duration 0, :start 306}],
                            :name     ":dev.playground/color"}
                           {:start    3,
                            :path     [[:dev.playground/id 1] :dev.playground/weight],
                            :duration 107,
                            :details  [{:event    "compute-plan",
                                        :duration 0,
                                        :start    4,
                                        :plan     (([:dev.playground/weight dev.playground/weight]))}
                                       {:event      "call-resolver-with-cache",
                                        :duration   0,
                                        :start      4,
                                        :input-data #:dev.playground{:id 1},
                                        :sym        dev.playground/weight,
                                        :key        :dev.playground/weight}
                                       {:event "call-read", :duration 0, :start 4}
                                       {:event      "schedule-resolver",
                                        :duration   0,
                                        :start      6,
                                        :input-data #:dev.playground{:id 1},
                                        :sym        dev.playground/weight,
                                        :key        :dev.playground/weight}
                                       {:event      "call-resolver",
                                        :duration   103,
                                        :start      6,
                                        :input-data #:dev.playground{:id 1},
                                        :sym        dev.playground/weight,
                                        :key        :dev.playground/weight}
                                       {:event    "merge-resolver-response",
                                        :duration 0,
                                        :start    109,
                                        :sym      dev.playground/weight,
                                        :key      :dev.playground/weight}
                                       {:event "call-read", :duration 0, :start 110}
                                       {:event "value-return", :duration 0, :start 110}],
                            :name     ":dev.playground/weight"}
                           {:start    6,
                            :path     [[:dev.playground/id 1] :dev.playground/color-darken],
                            :duration 325,
                            :details  [{:event    "compute-plan",
                                        :duration 1,
                                        :start    6,
                                        :plan     (([:dev.playground/color dev.playground/color]
                                                     [:dev.playground/color-darken dev.playground/darken-color]))}
                                       {:event "call-read", :duration 0, :start 6}
                                       {:event       "waiting-resolver",
                                        :duration    0,
                                        :start       7,
                                        :waiting-key :dev.playground/color,
                                        :input-data  #:dev.playground{:id 1},
                                        :sym         dev.playground/color,
                                        :key         :dev.playground/color-darken}
                                       {:event      "call-resolver-with-cache",
                                        :duration   0,
                                        :start      305,
                                        :input-data #:dev.playground{:color "red"},
                                        :sym        dev.playground/darken-color,
                                        :key        :dev.playground/color-darken}
                                       {:event      "schedule-resolver",
                                        :duration   0,
                                        :start      306,
                                        :input-data #:dev.playground{:color "red"},
                                        :sym        dev.playground/darken-color,
                                        :key        :dev.playground/color-darken}
                                       {:event      "call-resolver",
                                        :duration   24,
                                        :start      306,
                                        :input-data #:dev.playground{:color "red"},
                                        :sym        dev.playground/darken-color,
                                        :key        :dev.playground/color-darken}
                                       {:event    "merge-resolver-response",
                                        :duration 0,
                                        :start    330,
                                        :sym      dev.playground/darken-color,
                                        :key      :dev.playground/color-darken}
                                       {:event "call-read", :duration 0, :start 330}
                                       {:event "value-return", :duration 0, :start 331}],
                            :name     ":dev.playground/color-darken"}
                           {:start    9,
                            :path     [[:dev.playground/id 1] :dev.playground/size],
                            :duration 101,
                            :details  [{:event "skip-wait-key", :duration 0, :start 9}
                                       {:event "call-read", :duration 0, :start 110}
                                       {:event "value-return", :duration 0, :start 110}],
                            :name     ":dev.playground/size"}]}
               {:start    1,
                :path     [:com.wsscode.pathom/trace],
                :duration 0,
                :details  [{:event "compute-plan", :duration 0, :start 1}
                           {:event "call-read", :duration 0, :start 1}
                           {:event "value-return", :duration 0, :start 1}],
                :name     ":com.wsscode.pathom/trace"}],
    :hint     "Query"})

#_
(ws/defcard trace-view-card
  {::wsm/align {:flex 1}}
  (ct.fulcro/fulcro-card
    {::f.portal/root          trace/D3Trace
     ::f.portal/initial-state (fn [_]
                                {:trace-data sample-trace})}))
