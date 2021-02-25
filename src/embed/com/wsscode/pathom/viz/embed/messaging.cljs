(ns com.wsscode.pathom.viz.embed.messaging
  "Remote helpers to call remote parsers from the client."
  (:require [com.wsscode.pathom.viz.helpers :as h]
            [com.wsscode.pathom.viz.lib.hooks :as p.hooks]
            [goog.object :as gobj]
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
               (let [data (gobj/get msg "data")]
                 (if (= (gobj/get data "event")
                        "pathom-viz-embed")
                   (if-let [contents (decode (gobj/get data "payload"))]
                     (f contents)
                     (js/console.error "Failed to parse payload." data))))))]
    (p.hooks/use-event-listener js/window "message" cb)))
