(ns com.wsscode.pathom.viz.styles
  (:require [com.wsscode.pathom.viz.trace :as pvt]
            [com.wsscode.tailwind-garden.core :as tailwind]))

(def min-sizes
  [[:.min-w-40 {:min-width "10rem"}]
   [:.min-h-20 {:min-height "5rem"}]
   [:.min-h-40 {:min-height "10rem"}]])

(def full-css
  (reduce into
    [pvt/trace-css
     min-sizes]))
