(ns com.wsscode.pathom.viz.helpers
  (:require ["react-draggable" :refer [DraggableCore]]
            [clojure.pprint]
            [fulcro.client.dom :as dom]
            [fulcro.client.primitives :as fp]
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
