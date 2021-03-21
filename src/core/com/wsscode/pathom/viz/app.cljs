(ns com.wsscode.pathom.viz.app
  (:require [com.wsscode.pathom.viz.helpers :as h]))

(def ProviderContext (h/create-context #js {:parser-id nil}))
