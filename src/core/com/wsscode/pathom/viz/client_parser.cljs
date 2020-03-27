(ns com.wsscode.pathom.viz.client-parser
  (:require [clojure.spec.alpha :as s]
            [com.fulcrologic.guardrails.core :refer [>def >defn >fdef => | <- ?]]
            [com.wsscode.async.async-cljs :refer [let-chan]]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.core :as p]
            [edn-query-language.core :as eql]))

(>def ::parser-id any?)
(>def ::parsers (s/map-of ::parser-id ::p/parser))
(>def ::client-parser-request ::eql/query)
(>def ::client-parser-response map?)

(pc/defmutation client-parser-mutation
  [{::keys [parsers]}
   {::keys [parser-id client-parser-request]}]
  {::pc/params [::client-parser-request ::parser-id]
   ::pc/output [::client-parser-response]}
  (if-let [parser (get parsers parser-id)]
    (let-chan [response (parser {} client-parser-request)]
      {::client-parser-response response})
    (throw (ex-info "Parser not found" {::parser-id parser-id}))))
