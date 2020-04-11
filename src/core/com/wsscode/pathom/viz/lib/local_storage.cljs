(ns com.wsscode.pathom.viz.lib.local-storage
  (:refer-clojure :exclude [get set!])
  (:require [cljs.reader :refer [read-string]]
            [fulcro.inspect.remote.transit :as transit]))

(defn read-transit [s]
  (transit/read s))

(defn write-transit [x]
  (transit/write x))

(def local-storage (.-localStorage js/window))

;; edn

(defn get
  ([key] (get key nil))
  ([key default]
   (if-let [value (.getItem local-storage (pr-str key))]
     (read-string value)
     default)))

(defn set! [key value]
  (.setItem local-storage (pr-str key) (pr-str value)))

(defn update! [key f & args]
  (.setItem local-storage (pr-str key) (pr-str (apply f (get key) args))))

(defn remove! [key]
  (.removeItem local-storage key))

;; transit

(defn tget
  ([key] (tget key nil))
  ([key default]
   (if-let [value (.getItem local-storage (pr-str key))]
     (read-transit value)
     default)))

(defn tset! [key value]
  (.setItem local-storage (pr-str key) (write-transit value)))
