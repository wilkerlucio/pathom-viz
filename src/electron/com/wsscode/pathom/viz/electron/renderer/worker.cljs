(ns com.wsscode.pathom.viz.electron.renderer.worker
  (:require [com.wsscode.pathom3.interface.eql :as p.eql]
            [com.wsscode.async.processing :as wap]
            [com.wsscode.transit :as wt]
            [com.wsscode.async.async-cljs :refer [go-promise <! go <? <!p <?maybe]]
            [com.wsscode.pathom3.connect.operation :as pco]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
            [promesa.core :as p]
            [com.wsscode.pathom.viz.timeline :as timeline]
            [com.wsscode.pathom3.interface.async.eql :as p.a.eql]))

(defn send-message! [msg]
  (js/postMessage (wt/envelope-json msg))
  (wap/await! msg))

(pco/defresolver compute-trace [{:keys [pathom3/response]}]
  {:com.wsscode.pathom/trace
   (timeline/compute-timeline-tree response)})

(def env
  (pci/register
    [compute-trace]))

(defn handle-message [msg]
  (js/console.log "!! GOT MSG" msg)
  (case (:type msg)
    :eql
    (p/let [response (p.a.eql/process env (:tx msg))]
      (send-message! (wap/reply-message msg response)))

    (js/console.warn "Invalid message type" (:type msg))))

(defn init []
  (js/self.addEventListener "message"
    (fn [^js e]
      (let [msg (some-> e .-data wt/unpack-json)]
        (when-not (wap/capture-response! msg)
          (handle-message msg))))))

(init)
