(ns com.wsscode.pathom.viz.helpers
  (:require ["react-draggable" :refer [DraggableCore]]
            [cljs.core.async :refer [go <!]]
            [cljs.spec.alpha :as s]
            [clojure.pprint]
            [clojure.walk :as walk]
            [com.fulcrologic.fulcro.algorithms.tx-processing :as txn]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.fulcrologic.fulcro.components :as fp]
            [com.fulcrologic.fulcro.mutations :as fm]
            [com.fulcrologic.guardrails.core :refer [>def >defn >fdef => | <- ?]]
            [com.wsscode.common.async-cljs :refer [<?maybe]]
            [com.wsscode.pathom.core :as p]
            [edn-query-language.core :as eql]
            [goog.object :as gobj]))

(>def ::path (s/coll-of keyword? :kind vector?))
(>def ::path-map "The tree of maps" (s/map-of ::path map?))
(>def ::node (s/keys :opt-un [::children]))
(>def ::children (s/coll-of ::node))

(defn pd [f]
  (fn [e]
    (.preventDefault e)
    (f e)))

(defn target-value [e] (gobj/getValueByKeys e "target" "value"))

(defn stringify-keyword-values [x]
  (walk/prewalk
    (fn [x]
      (cond
        (simple-keyword? x)
        (name x)

        (keyword x)
        (str x)

        :else
        x))
    x))

(defn pprint-str [x]
  (with-out-str
    (clojure.pprint/pprint x)))

(defn drag-resize [this {:keys [attribute default axis props] :or {axis "y"}} child]
  (js/React.createElement DraggableCore
    #js {:key     "dragHandler"
         :onStart (fn [e dd]
                    (gobj/set this "start" (gobj/get dd axis))
                    (gobj/set this "startSize" (or (fp/get-state this attribute) default)))
         :onDrag  (fn [e dd]
                    (let [start    (gobj/get this "start")
                          size     (gobj/get this "startSize")
                          value    (gobj/get dd axis)
                          new-size (+ size (if (= "x" axis) (- value start) (- start value)))]
                      (fp/set-state! this {attribute new-size})))}
    (dom/div (merge {:style {:pointerEvents "all"
                             :cursor        (if (= "x" axis) "ew-resize" "ns-resize")}}
               props)
      child)))

(defn pprint [x]
  (with-out-str (cljs.pprint/pprint x)))

(defn map-vals [f m]
  (into {} (fn [[k v]] [k (f v)]) m))

(defn index-by [f coll]
  (persistent!
    (reduce
      (fn [ret x]
        (let [k (f x)]
          (assoc! ret k x)))
      (transient {}) coll)))

(fm/defmutation update-value [{:keys [key fn args]}]
  (action [{:keys [state ref]}]
    (swap! state update-in ref update key #(apply fn % args))))

(defn update-value!
  "Helper to call transaction to update some key from current component."
  [component key fn & args]
  (fp/transact! component [`(update-value {:key ~key :fn ~fn :args ~args})]))

(defn toggle-set-item [set item]
  (if (contains? set item)
    (disj set item)
    (conj set item)))

(defn vector-compare [[value1 & rest1] [value2 & rest2]]
  (let [result (compare value1 value2)]
    (cond
      (not (zero? result)) result
      (nil? value1) 0
      :else (recur rest1 rest2))))

(defn remove-not-found [x]
  (p/transduce-maps
    (remove (fn [[_ v]] (contains? #{::p/not-found ::fp/not-found} v)))
    x))

(>defn path-map->tree
  "Generate a tree structure from a map of maps to data. For example, the given structure:

  {[:a] {:any data}
   [:a :b] {:more data}
   [:c] {:key foo}}

   It will return:

   {:children [{:any data
                :children [{:more data}]}
               {:key foo}]}"
  [path-map]
  [::path-map => ::node]
  (let [{:keys [items]}
        (reduce
          (fn [{:keys [items index]} path]
            (if (> (count path) 1)
              (let [prev (subvec path 0 (dec (count path)))]
                {:items items
                 :index (update-in index [prev :children] (fnil conj [])
                          (get index path))})
              {:items (conj items (get index path))
               :index index}))
          {:items []
           :index path-map}
          (->> path-map
               (keys)
               (sort #(vector-compare %2 %))))]
    {:children items}))

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
                      (ok-handler {:transaction edn :body (<?maybe (parser {} edn))})
                      (catch :default e
                        (js/console.error "Pathom Remote error:" e)
                        (error-handler {:body e}))))))})
