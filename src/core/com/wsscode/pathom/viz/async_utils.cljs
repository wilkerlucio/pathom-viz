(ns com.wsscode.pathom.viz.async-utils
  (:require [cljs.spec.alpha :as s]
            [clojure.core.async :as async :refer [go <! chan go-loop]]
            [com.fulcrologic.guardrails.core :refer [>def >defn >fdef => | <- ?]]
            [com.wsscode.async.async-cljs :refer [<!maybe go-promise]]))

(>def ::channel any?)
(>def ::request-id any?)
(>def ::response-id any?)
(>def ::request-response any?)

(def ^:dynamic *timeout* 5000)

(defonce response-notifiers* (atom {}))

(>defn await-response!
  [{::keys [request-id] :as msg}]
  [::request-id => ::channel]
  (let [chan  (async/promise-chan)
        timer (async/timeout *timeout*)]
    (swap! response-notifiers* assoc request-id chan)
    (go-promise
      (let [[val ch] (async/alts! [chan timer] :priority true)]
        (swap! response-notifiers* dissoc request-id)
        (if (= ch timer)
          (ex-info "Response timeout" {:timeout      *timeout*
                                       ::request-id  request-id
                                       :request-keys (keys msg)})
          val)))))

(>defn await! [{::keys [request-id] :as msg}]
  [(s/keys :opt [::request-id])
   => (? ::channel)]
  (if request-id
    (await-response! msg)))

(>defn resolve-response
  "Notify to a responder that the data is ready."
  [request-id msg]
  [::request-id any? => any?]
  (if-let [chan (get @response-notifiers* request-id)]
    (async/put! chan msg)
    (js/console.warn "Tried to notify unavailable responder" request-id)))

(>defn capture-response!
  [{::keys [response-id request-response]}]
  [(? (s/keys :opt [::response-id ::request-response]))
   => boolean?]
  (if request-response
    (do
      (resolve-response response-id request-response)
      true)
    false))

(>defn reply-message
  "Helper to make a response map for a given message with a request-id.

  Use this to generate response data from async events."
  [{::keys [request-id]} value]
  [(s/keys :req [::request-id]) any?
   => (s/keys :req [::response-id ::request-response])]
  {::response-id      request-id
   ::request-response value})

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
