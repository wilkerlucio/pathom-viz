(ns com.wsscode.pathom.viz.worker
  (:require [com.wsscode.async.processing :as wap]
            [com.wsscode.pathom.viz.helpers :as pvh]
            [com.wsscode.promesa.bridges.core-async]
            [com.wsscode.transit :as wt]
            [helix.core :as h]
            [helix.hooks :as hooks]
            [promesa.core :as p]))

(defn send-message! [worker msg]
  (.postMessage worker (wt/envelope-json msg))
  (wap/await! msg))

(def WorkerContext (h/create-context nil))

(def WORKER_PATH "js/renderer/worker.js")

(defn use-worker
  [{:keys [path callback]}]
  (let [path    (or path WORKER_PATH)
        !worker (pvh/use-fstate nil)]
    (hooks/use-effect [path]
      (if path
        (let [worker (js/Worker. path)]
          (.addEventListener worker "message"
            (fn [e]
              (let [msg (some-> e .-data wt/unpack-json)]
                (when-not (wap/capture-response! msg)
                  (if callback (callback e msg))))))

          (!worker worker)

          #(.terminate worker))))
    @!worker))

(defn eql-request [eql]
  {:type            :eql
   :tx              eql
   ::wap/timeout    3600000
   ::wap/request-id (wap/random-request-id)})

(defn use-worker-response [worker msg]
  (let [!data (pvh/use-fstate nil)]
    (hooks/use-effect [worker (-> msg (dissoc ::wap/request-id) (hash))]
      (if worker
        (p/let [response (send-message! worker msg)]
          (js/console.log "!! WORKER RESPONSE" response)
          (!data response))))
    @!data))

(defn use-worker-eql
  ([worker eql]
   (use-worker-response worker (eql-request eql)))
  ([worker data eql]
   (-> worker
       (use-worker-response (eql-request [{(list :>/container data) eql}]))
       (get :>/container))))
