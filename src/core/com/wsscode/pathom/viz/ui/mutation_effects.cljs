(ns com.wsscode.pathom.viz.ui.mutation-effects
  (:require [com.fulcrologic.fulcro.mutations :as fm]))

#_ :clj-kondo/ignore
(fm/defmutation open-external [_]
  (remote [_] true))
