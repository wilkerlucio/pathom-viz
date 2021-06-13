(ns com.wsscode.pathom.viz.lib.fetch
  (:require [cljs.reader :refer [read-string]]
            [cljs.spec.alpha :as s]
            [com.fulcrologic.guardrails.core :refer [>def >defn >fdef => | <- ?]]
            [com.wsscode.async.async-cljs :refer [go-promise <!p <?]]
            [com.wsscode.transito :as tt]
            [com.wsscode.pathom3.connect.operation.transit :as pcot]
            [goog.object :as gobj]))

; region specs

(>def ::headers (s/map-of string? string?))

(>def ::cache-key any?)

(>def ::request-country (s/with-gen string? #(s/gen #{"br" "mx"})))
(>def ::request-environment #{"staging" "prod"})
(>def ::request-prototype string?)
(>def ::request-service (s/and qualified-keyword? #(= "nubank.service" (namespace %))))
(>def ::request-path string?)
(>def ::request-url string?)
(>def ::request-method #{"GET" "POST" "PATCH" "HEAD" "OPTION" "PUT"})
(>def ::request-headers ::headers)
(>def ::request-body any?)
(>def ::request-body-text string?)
(>def ::request-as #{::request-as-transit
                     ::request-as-edn
                     ::request-as-json
                     ::request-as-text})
(>def ::request-auth-token string?)
(>def ::request-cid string?)

(>def ::request
  (s/keys :req [::request-url]
          :opt [::request-method
                ::request-headers
                ::request-body]))

(>def ::response-body-text string?)
(>def ::response-body any?)
(>def ::response-headers ::headers)
(>def ::response-status (s/int-in 100 600))

(>def ::response
  (s/keys :req [::response-status ::response-body-text ::response-headers]
    :opt [::response-body]))

; endregion

; region normalization

(defonce cached* (atom {}))

(defn normalize-request-body [{::keys [request-body request-as] :as request}]
  (if (contains? request ::request-body)
    (if (string? request-body)
      (assoc request ::request-body-text request-body)
      (case request-as
        ::request-as-transit
        (assoc request ::request-body-text (tt/write-str request-body {:handlers pcot/write-handlers}))

        ::request-as-edn
        (assoc request ::request-body-text (pr-str request-body))

        ::request-as-json
        (assoc request ::request-body-text (js/JSON.stringify (clj->js request-body)))

        ::request-as-text
        (assoc request ::request-body-text (str request-body))

        request))
    request))

(def content-type-mapping
  {::request-as-transit "application/transit+json; charset=UTF-8"
   ::request-as-edn     "application/edn; charset=UTF-8"
   ::request-as-json    "application/json; charset=UTF-8"
   ::request-as-text    "plain/text; charset=UTF-8"})

(defn default-request-header
  "Set a request header, unless its already set."
  [request k v]
  (update request ::request-headers #(merge {k v} %)))

(defn normalize-request-content-type [{::keys [request-as] :as request}]
  (if-let [content-type (get content-type-mapping request-as)]
    (default-request-header request "Content-Type" content-type)
    request))

(defn normalize-request-accept [{::keys [request-as] :as request}]
  (if-let [content-type (get content-type-mapping request-as)]
    (default-request-header request "Accept" content-type)
    request))

(defn normalize-request-auth-token [{::keys [request-auth-token] :as request}]
  (if request-auth-token
    (default-request-header request "Authorization" (str "Bearer " request-auth-token))
    request))

(def cid-chars "ABCDEFGHIJKLMNOPQRSTUVXYWZ0123456789")

(defn random-cid-fragment []
  (apply str (repeatedly 5 (partial rand-nth cid-chars))))

(defn normalize-request-cid [{::keys [request-cid] :as request}]
  (let [cid (or request-cid (str "NuFetchCLJS." (random-cid-fragment)))]
    (default-request-header request "X-Correlation-ID" cid)))

(>defn normalize-request [request-options]
  [(s/keys) => (s/keys)]
  (-> request-options
      (normalize-request-body)
      (normalize-request-content-type)
      (normalize-request-accept)
      (normalize-request-auth-token)
      (normalize-request-cid)))

(defn normalize-response-body [{::keys [response-body-text request-as] :as response}]
  (case request-as
    ::request-as-transit
    (assoc response ::response-body (tt/read-str response-body-text {:handlers pcot/read-handlers}))

    ::request-as-edn
    (assoc response ::response-body (read-string response-body-text))

    ::request-as-json
    (assoc response ::response-body (js->clj (js/JSON.parse response-body-text)))

    (assoc response ::response-body response-body-text)))

(defn normalize-response [response]
  (-> response
      (normalize-response-body)))

; endregion

; region js fetch

(defn- js-fetch-req-method [request {::keys [request-method]}]
  (assoc request :method (or request-method "GET")))

(defn- js-fetch-req-headers [request {::keys [request-headers]}]
  (cond-> request
    request-headers (assoc :headers request-headers)))

(defn- js-fetch-req-body [request {::keys [request-body-text]}]
  (cond-> request
    request-body-text (assoc :body request-body-text)))

(>defn js-fetch-req-params [options]
  [::request => any?]
  (-> {}
      (js-fetch-req-method options)
      (js-fetch-req-headers options)
      (js-fetch-req-body options)
      clj->js))

(defn js-fetch-http-client
  "Default driver, implements using js Fetch API."
  [{::keys [request-url] :as options}]
  (go-promise
    (let [response (<!p (js/fetch request-url (js-fetch-req-params options)))
          text     (<!p (.text response))
          headers  (gobj/get response "headers")
          status   (gobj/get response "status")]
      (assoc options
        ::response-status status
        ::response-body-text text
        ::response-headers headers))))

; endregion

(defn fetch*
  [{::keys [http-client] :as request}]
  (let [http-client (or http-client js-fetch-http-client)]
    (go-promise
      (-> request
          (normalize-request)
          (http-client) <?
          (normalize-response)))))

(defn fetch
  "Trigger HTTP requests.

  Fetch here is a generic API to do HTTP requests, by default it will use the browser Fetch API, but
  you can replace it by changing the ::http-client key.

  Basic fetch request example:

      (nuf/fetch {::nuf/request-url \"/some-page.edn\"
                  ::nuf/request-as  ::nuf/request-as-edn})

  POST example:

      (nuf/fetch {::nuf/request-url    \"/do-something\"
                  ::nuf/request-method \"POST\"
                  ::nuf/request-body   {:some \"data\"}
                  ::nuf/request-as     ::nuf/request-as-edn})

  The `::nuf/as` will set proper content-type and encode the dart.

  Fetch also supports building the URL from parts, example:


      (nuf/fetch {::nuf/request-service     :nubank.service/auth
                  ::nuf/request-path        \"/api/token\"
                  ::nuf/request-country     \"mx\"
                  ::nuf/request-environment \"prod\"
                  ::nuf/request-prototype   \"s2\"})

  If you like to cache some request, there is a built-in feature you can use, just send
  the `::nuf/cache-key` param, after the first successful access it will load the next
  requests from the cache.
  "
  [{::keys [cache-key] :as request}]
  (if cache-key
    (-> (swap! cached*
          (fn [x]
            (if (contains? x cache-key)
              x
              (assoc x cache-key (fetch* request)))))
        (get cache-key))
    (fetch* request)))

(defn fetch-body
  "Like fetch, but returns the content body directly instead of the full response data."
  [request]
  (go-promise
    (-> request
        (fetch) <?
        ::response-body)))
