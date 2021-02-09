(ns com.wsscode.pathom.viz.embed.messaging
  "Remote helpers to call remote parsers from the client."
  (:require [cljs.reader :refer [read-string]]
            [com.wsscode.pathom.viz.helpers :as h]
            [com.wsscode.pathom.viz.lib.hooks :as p.hooks]
            [helix.hooks :as hooks]))

(defn decode [s]
  (h/safe-read-string s))

(defn query-param-state []
  (some-> js/window.location.search
          (js/URLSearchParams.)
          (.get "msg")
          decode))

(defn use-post-message-data [f]
  (let [cb (hooks/use-callback [f]
             (fn [^js msg]
               (when-let [contents (decode (.-data msg))]
                 (f contents))))]
    (p.hooks/use-event-listener js/window "message" cb)))
