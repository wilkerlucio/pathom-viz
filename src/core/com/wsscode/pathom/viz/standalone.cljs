(ns com.wsscode.pathom.viz.standalone
  (:require [cognitect.transit :as transit]
            [com.wsscode.pathom.diplomat.http :as p.http]
            [com.wsscode.pathom.diplomat.http.fetch :as p.http.fetch]
            [com.wsscode.pathom.fulcro.network :as p.network]
            [com.wsscode.pathom.viz.query-editor :as pv.query-editor]
            [com.wsscode.common.async-cljs :refer [<? go-catch]]
            [fulcro.client :as fc]
            [fulcro.client.network :as net]
            [fulcro.client.data-fetch :as df]
            [fulcro.client.primitives :as fp]
            [fulcro-css.css :as css]))


(defn transit-read [x]
  (-> (transit/read (transit/reader :json) x)))

(defn transit-write [x]
  (-> (transit/write (transit/writer :json) x)))

(defn http-request-parser [url]
  (fn [env tx]
    (go-catch
      (let [{::p.http/keys [body]}
            (<? (p.http/request {::p.http/driver       p.http.fetch/request-async
                                 ::p.http/url          url
                                 ::p.http/content-type ::p.http/transit+json
                                 ::p.http/method       ::p.http/post
                                 ::p.http/headers      {}
                                 ::p.http/form-params  (transit-write tx)}))]
        (transit-read body)))))


(fp/defsc Root
  [this {:keys [ui/root]}]
  {:query [{:ui/root (fp/get-query pv.query-editor/QueryEditor)}]
   :initial-state (fn [params] {:ui/root (fp/get-initial-state  pv.query-editor/QueryEditor {})})}
  (pv.query-editor/query-editor root))

(def root (fp/factory Root))

(css/upsert-css "pv-queryeditor-style" pv.query-editor/QueryEditor)
(css/upsert-css "rootui-style" Root)

(def default-url "/pathom")

(defn ^:export init []
  (let [app (fc/make-fulcro-client
              {:client-did-mount
               (fn [app]
                 (pv.query-editor/load-indexes app))
               :networking
               (let [url js/document.location.pathname
                     url (if (= url "/") default-url url)]
                {pv.query-editor/remote-key
                 (p.network/pathom-remote
                   (pv.query-editor/client-card-parser (http-request-parser url)))})})]
    (fc/mount app Root (js/document.getElementById "app"))))
