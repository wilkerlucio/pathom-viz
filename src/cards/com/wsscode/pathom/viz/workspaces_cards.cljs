(ns com.wsscode.pathom.viz.workspaces-cards
  (:require [cljs.core.async :as async :refer [go <!]]
            [com.wsscode.common.async-cljs :refer [<? go-catch]]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.fulcro.network :as pfn]
            [com.wsscode.pathom.viz.codemirror :as cm]
            [nubank.workspaces.card-types.fulcro :as ct.fulcro]
            [nubank.workspaces.card-types.react :as ct.react]
            [nubank.workspaces.core :as ws]
            [nubank.workspaces.lib.fulcro-portal :as f.portal]
            [nubank.workspaces.model :as wsm]
            [com.wsscode.pathom.viz.workspaces :as pvw]
            [fulcro.client.primitives :as fp]
            [fulcro.client.localized-dom :as dom]))

(def indexes (atom {}))

(defmulti resolver-fn pc/resolver-dispatch)
(def defresolver (pc/resolver-factory resolver-fn indexes))

(defmulti mutation-fn pc/mutation-dispatch)
(def defmutation (pc/mutation-factory mutation-fn indexes))

(def parser
  (p/parallel-parser {::p/env     (fn [env]
                                    (merge
                                      {::p/reader             [p/map-reader pc/parallel-reader pc/ident-reader pc/index-reader]
                                       ::pc/resolver-dispatch resolver-fn
                                       ::pc/indexes           (assoc-in @indexes [::pc/index-io #{} :com.wsscode.pathom/trace] {})}
                                      env))
                      ::p/mutate  pc/mutate-async
                      ::p/plugins [p/error-handler-plugin
                                   p/request-cache-plugin
                                   p/trace-plugin
                                   (p/post-process-parser-plugin p/elide-not-found)]}))

(def email->name
  {"wilkerlucio@gmail.com" {:first-name "Wilker"
                            :last-name  "Silva"}
   "power@ranger.com"      {:first-name "Power"
                            :last-name  "Ranger"}
   "spy@ranger.com"        {:first-name "Spy"
                            :last-name  "Ranger"}})

(defresolver `email->name
  {::pc/input  #{:email}
   ::pc/output [:first-name :last-name]
   ::pc/batch? true}
  (pc/batch-resolver
    (fn [_ {:keys [email]}]
      (go
        (<! (async/timeout 40))
        (get email->name email)))
    (fn [_ inputs]
      (go
        (<! (async/timeout 50))
        (mapv #(get email->name (:email %)) inputs)))))

(defresolver `full-name
  {::pc/input  #{:first-name :last-name}
   ::pc/output [:full-name]}
  (fn [_ {:keys [first-name last-name]}]
    {:full-name (str first-name " " last-name)}))

(defresolver `wanted-emails
  {::pc/output [{:wanted-emails [:email]}]}
  (fn [_ _]
    {:wanted-emails [{:email "power@ranger.com"}
                     {:email "spy@ranger.com"}
                     {:email "wilkerlucio@gmail.com"}]}))

(defresolver `pi
  {::pc/output [:pi]}
  (fn [_ _]
    (go
      (<! (async/timeout 10))
      {:pi js/Math.PI})))

(ws/defcard conj-demo-card
  (pvw/pathom-card {::pvw/parser parser})
  #_{::wsm/align {:flex "1"}})
