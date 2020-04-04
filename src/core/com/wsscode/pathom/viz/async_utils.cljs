(ns com.wsscode.pathom.viz.async-utils
  (:require [cljs.spec.alpha :as s]
            [clojure.core.async :as async :refer [go <! chan go-loop]]
            [com.fulcrologic.guardrails.core :refer [>def >defn >fdef => | <- ?]]
            [com.wsscode.async.async-cljs :refer [<!maybe go-promise]]))

(>def ::channel any?)
(>def ::response-id any?)

(def ^:dynamic *timeout* 5000)

(defonce response-notifiers* (atom {}))

(>defn await-response!
  [response-id]
  [::response-id => ::channel]
  (let [chan  (async/promise-chan)
        timer (async/timeout *timeout*)]
    (swap! response-notifiers* assoc response-id chan)
    (go-promise
      (let [[val port] (async/alts! [chan timer] :priority true)]
        (swap! response-notifiers* dissoc response-id)
        (if (= port chan)
          val
          (ex-info "Response timeout" {:timeout      *timeout*
                                       ::response-id response-id}))))))

(>defn await! [{::keys [response-id]}]
  [(s/keys :opt [::response-id])
   => (? ::channel)]
  (if response-id
    (await-response! response-id)))

(>defn resolve-response
  "Notify to a responder that the data is ready."
  [response-id msg]
  [::response-id any? => any?]
  (if-let [chan (get @response-notifiers* response-id)]
    (async/put! chan msg)
    (js/console.warn "Tried to notify unavailable responder" response-id)))

(>defn event-queue!
  "Add listener to object in event-name using `.on`, this will use a core
  async channel as queue, the handler function can return a channel so the
  channel will be awaited upon before handling the next message.

  If you want to be able to stop the processing, send your own channel as config
  and when you need to finish, close the channel."
  ([handler] [fn? => any?]
   (event-queue! {} handler))
  ([{::keys [channel]} handler]
   [(s/keys :opt [::channel]) fn?
    => any?]
   (let [channel' (or channel (chan (async/dropping-buffer 1024)))]
     (go-loop []
       (when-let [args (<! channel')]
         (<!maybe (apply handler args))
         (recur)))

     (fn [& args] (async/put! channel' args)))))
