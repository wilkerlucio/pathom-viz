(ns com.wsscode.pathom3.viz.ui
  (:require
    [cljs.tools.reader :refer [read-string]]
    [com.wsscode.js.browser-local-storage :as ls]
    [com.wsscode.misc.coll :as coll]
    [helix.dom :as dom]
    [helix.hooks :as hooks]))

(defn state-hook-serialize [[value set-value!]]
  [(pr-str value) #(set-value! (read-string %))])

(defn dom-props [{::keys [state] :as props}]
  (cond-> (coll/filter-keys simple-keyword? props)
    state
    (as-> <>
      (let [[value set-value!] state]
        (assoc <> :value value :on-change #(set-value! (.. % -target -value)))))))

(defn dom-select
  [{::keys [options] :as props}]
  (dom/select {:& (-> props
                      (coll/update-if ::state state-hook-serialize)
                      (dom-props))}
    (for [[value label] options]
      (let [value-str (pr-str value)]
        (dom/option {:value value-str :key value-str} (str label))))))

(defn use-persistent-state [store-key initial-value]
  (let [[value set-value!] (hooks/use-state (ls/get store-key initial-value))
        set-persistent! (fn [x] (ls/set! store-key x) (doto x set-value!))]
    [value set-persistent!]))
