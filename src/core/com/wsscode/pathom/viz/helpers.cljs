(ns com.wsscode.pathom.viz.helpers
  (:require ["react-draggable" :refer [DraggableCore]]
            [clojure.pprint]
            [fulcro.client.dom :as dom]
            [fulcro.client.primitives :as fp]
            [fulcro.client.mutations :as fm]
            [goog.object :as gobj]
            [clojure.walk :as walk]))

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
