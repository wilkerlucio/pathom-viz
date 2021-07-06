(ns com.wsscode.pathom.viz.lib.local-storage
  (:refer-clojure :exclude [get set!])
  (:require [cljs.reader :refer [read-string]]
            [com.wsscode.transito :as transit]))

(defn read-transit [s]
  (transit/read-str s))

(defn write-transit [x]
  (transit/write-str x))

;; edn

(defn get
  ([key] (get key nil))
  ([key default]
   (if-let [value (.getItem (.-localStorage js/window) (pr-str key))]
     (read-string value)
     default)))

(defn set! [key value]
  (.setItem (.-localStorage js/window) (pr-str key) (pr-str value)))

(defn update! [key f & args]
  (.setItem (.-localStorage js/window) (pr-str key) (pr-str (apply f (get key) args))))

(defn remove! [key]
  (.removeItem (.-localStorage js/window) key))

;; transit

(defn tget
  ([key] (tget key nil))
  ([key default]
   (if-let [value (.getItem (.-localStorage js/window) (pr-str key))]
     (read-transit value)
     default)))

(defn tset! [key value]
  (.setItem (.-localStorage js/window) (pr-str key) (write-transit value)))
