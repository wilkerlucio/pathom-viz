(ns com.wsscode.js.browser-local-storage
  (:refer-clojure :exclude [get set!])
  (:require
    [cljs.reader :refer [read-string]]))

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
