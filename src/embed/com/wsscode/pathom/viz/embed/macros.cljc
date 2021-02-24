(ns com.wsscode.pathom.viz.embed.macros
  (:require [helix.core :as h]))

(defmacro defc [sym arglist & body]
  (let [display-name (symbol (str sym "-class"))]
    `(do
       (h/defnc ~display-name [{:keys [~'value ~'children]}]
         (let [~(first arglist) ~'value
               ~@(if-let [s (second arglist)]
                   [s 'children])]
           ~@body))

       (defn ~sym [props# ~'& children#]
         (h/$ ~display-name {:value props#
                             :children children#})))))

(comment
  (macroexpand-1 `(defc ~'foo [~'p]
                    nil)))
