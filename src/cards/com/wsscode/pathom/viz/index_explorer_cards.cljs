(ns com.wsscode.pathom.viz.index-explorer-cards
  (:require [cljs.reader :refer [read-string]]
            [com.wsscode.common.async-cljs :refer [go-catch <?]]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.diplomat.http :as p.http]
            [com.wsscode.pathom.diplomat.http.fetch :as p.http.fetch]
            [com.wsscode.pathom.fulcro.network :as p.network]
            [com.wsscode.pathom.viz.index-explorer :as iex]
            [fulcro.client.data-fetch :as df]
            [nubank.workspaces.card-types.fulcro :as ct.fulcro]
            [nubank.workspaces.core :as ws]
            [nubank.workspaces.lib.fulcro-portal :as f.portal]
            [nubank.workspaces.model :as wsm]
            [fulcro.client.primitives :as fp]
            [cljs.test :refer [is]]))

(pc/defresolver index-resolver [env _]
  {::pc/output [::sample-index]}
  (go-catch
    (let [{::iex/keys [id]} (-> env :ast :params)
          index (-> (p.http/request env "index.edn" {::p.http/as ::text})
                    <? ::p.http/body read-string)]
      {::sample-index {::iex/id    id
                       ::iex/index index}})))

(def parser
  (p/parallel-parser
    {::p/env     {::p/reader               [p/map-reader
                                            pc/parallel-reader
                                            pc/open-ident-reader
                                            p/env-placeholder-reader]
                  ::p/placeholder-prefixes #{">"}
                  ::p.http/driver          p.http.fetch/request-async}
     ::p/mutate  pc/mutate-async
     ::p/plugins [(pc/connect-plugin {::pc/register [index-resolver]})
                  (p/post-process-parser-plugin p/elide-not-found)
                  p/error-handler-plugin
                  p/request-cache-plugin
                  p/trace-plugin]}))

(ws/defcard index-explorer
  {::wsm/align ::wsm/stretch-flex}
  (let [id (random-uuid)]
    (ct.fulcro/fulcro-card
      {::f.portal/root iex/IndexExplorer
       ::f.portal/app  {:networking       (-> (p.network/pathom-remote parser)
                                              (p.network/trace-remote))
                        :initial-state    {::id id}
                        :started-callback (fn [app]
                                            (df/load app ::sample-index iex/IndexExplorer
                                              {:params {::iex/id id}
                                               :target [:ui/root]}))}})))

(fp/defsc AttributeGraphDemo
  [this {::keys []}]
  {:pre-merge     (fn [{:keys [current-normalized data-tree]}]
                    (merge {:ui/id (random-uuid)} current-normalized data-tree))
   :initial-state {}
   :ident         [:ui/id :ui/id]
   :query         [:ui/id]
   :css-include   [iex/AttributeGraph]}
  (iex/attribute-graph {::pc/attribute :customer/id}))

(ws/defcard attribute-graph-card
  {::wsm/card-width 4 ::wsm/card-height 12}
  (ct.fulcro/fulcro-card
    {::f.portal/root AttributeGraphDemo}))

(defn simple-compute-nodes-out [out]
  (-> out
      (update :nodes #(mapv (fn [x] (select-keys x [:attribute])) %))))

(ws/deftest test-attribute-network
  (is (= (-> (iex/attribute-network {::iex/attributes [{}]} :movie/name)))))

(ws/deftest test-compute-nodes-links
  (is (= (iex/compute-nodes-links {})
         {:nodes [] :links []}))
  (is (= (iex/compute-nodes-links
           {::iex/attributes [{::pc/attribute :movie/name}]})
         {:nodes [{:attribute ":movie/name"
                   :multiNode false
                   :mainNode  nil
                   :weight    nil
                   :reach     nil
                   :radius    3}]
          :links []}))
  (is (= (-> (iex/compute-nodes-links
               {::iex/attributes [{::pc/attribute      :movie/name
                                   ::pc/attr-reach-via {#{:movie/id} #{'movie-by-id}}}
                                  {::pc/attribute     :movie/id
                                   ::pc/attr-provides {:movie/name #{'movie-by-id}}}]})
             simple-compute-nodes-out)
         {:nodes [{:attribute ":movie/name"}
                  {:attribute ":movie/id"}]
          :links [{:source      ":movie/id"
                   :target      ":movie/name"
                   :lineProvide true}]}))
  (is (= (-> (iex/compute-nodes-links
               {::iex/nested-provides? true
                ::iex/attributes [{::pc/attribute      :movie/name
                                   ::pc/attr-reach-via {[#{:movie/id :movie/latest}] #{'latest-movie}}}
                                  {::pc/attribute     :movie/id
                                   ::pc/attr-provides {[:movie/latest :movie/name] #{'latest-movie}}}]})
             simple-compute-nodes-out)
         {:nodes [{:attribute ":movie/name"}
                  {:attribute ":movie/id"}]
          :links [{:source      ":movie/id"
                   :target      ":movie/name"
                   :deep        true
                   :lineProvide true}]})))
