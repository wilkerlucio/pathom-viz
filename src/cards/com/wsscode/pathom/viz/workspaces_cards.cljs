(ns com.wsscode.pathom.viz.workspaces-cards
  (:require [cljs.core.async :as async :refer [go <!]]
            [com.wsscode.common.async-cljs :refer [<? go-catch]]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.core :as p]
            [nubank.workspaces.model :as wsm]
            [nubank.workspaces.core :as ws]
            [com.wsscode.pathom.viz.workspaces :as pvw]))

(def indexes (atom {}))

(defmulti resolver-fn pc/resolver-dispatch)
(def defresolver (pc/resolver-factory resolver-fn indexes))

(defmulti mutation-fn pc/mutation-dispatch)
(def defmutation (pc/mutation-factory mutation-fn indexes))

(def parser
  (p/parallel-parser {::p/env          (fn [env]
                                         (merge
                                           {::p/reader             [p/map-reader pc/parallel-reader pc/ident-reader]
                                            ::pc/resolver-dispatch resolver-fn
                                            ::pc/mutate-dispatch   mutation-fn
                                            ::pc/indexes           @indexes}
                                           env))
                      ::pc/defresolver defresolver
                      ::pc/defmutation defmutation
                      ::p/mutate       pc/mutate-async
                      ::p/plugins      [p/error-handler-plugin
                                        p/request-cache-plugin
                                        p/trace-plugin
                                        pc/connect-plugin]}))

(def email->name
  {"wilkerlucio@gmail.com" {:first-name "Wilker"
                            :last-name  "Silva"}
   "power@ranger.com"      {:first-name "Power"
                            :last-name  "Ranger"}
   "spy@ranger.com"        {:first-name "Spy"
                            :last-name  "Ranger"}
   "dull1@dall.com"        {:first-name "Hi 1"
                            :last-name  "Hey"}
   "dull2@dall.com"        {:first-name "Hi 2"
                            :last-name  "Hey"}
   "dull3@dall.com"        {:first-name "Hi 3"
                            :last-name  "Hey"}
   "dull4@dall.com"        {:first-name "Hi 4"
                            :last-name  "Hey"}
   "dull5@dall.com"        {:first-name "Hi 5"
                            :last-name  "Hey"}
   "dull6@dall.com"        {:first-name "Hi 6"
                            :last-name  "Hey"}
   "dull7@dall.com"        {:first-name "Hi 7"
                            :last-name  "Hey"}
   "dull8@dall.com"        {:first-name "Hi 8"
                            :last-name  "Hey"}
   "dull9@dall.com"        {:first-name "Hi 9"
                            :last-name  "Hey"}
   "dull10@dall.com"       {:first-name "Hi 10"
                            :last-name  "Hey"}})

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
    {:wanted-emails (mapv #(hash-map :email %) (keys email->name))}))

(defresolver `pi
  {::pc/output [:pi]}
  (fn [_ _]
    (go
      (<! (async/timeout 10))
      {:pi js/Math.PI})))

(ws/defcard simple-parser-demo
  (pvw/pathom-card {::pvw/parser parser})
  #_{::wsm/align {:flex "1"}})
