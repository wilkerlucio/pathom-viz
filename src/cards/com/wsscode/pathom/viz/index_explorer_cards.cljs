(ns com.wsscode.pathom.viz.index-explorer-cards
  (:require [cljs.reader :refer [read-string]]
            [cljs.spec.alpha :as s]
            [cljs.test :refer [is testing]]
            [com.wsscode.common.async-cljs :refer [go-catch <?]]
            [com.wsscode.fuzzy :as fuzzy]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.diplomat.http :as p.http]
            [com.wsscode.pathom.diplomat.http.fetch :as p.http.fetch]
            [com.wsscode.pathom.viz.index-explorer :as iex]
            [com.wsscode.pathom.viz.ui.kit :as ui]
            [com.wsscode.pathom.viz.workspaces :as pws]
            [edn-query-language.core :as eql]
            [fulcro.client.localized-dom :as dom]
            [fulcro.client.primitives :as fp]
            [nubank.workspaces.card-types.fulcro :as ct.fulcro]
            [nubank.workspaces.core :as ws]
            [nubank.workspaces.lib.fulcro-portal :as f.portal]
            [nubank.workspaces.model :as wsm]))

(s/def :customer/id uuid?)

(pc/defresolver index-resolver [env {::iex/keys [id]}]
  {::pc/input  #{::iex/id}
   ::pc/output [::iex/id ::iex/index]}
  (go-catch
    (let [index (-> (p.http/request env "index.edn" {::p.http/as ::text})
                    <? ::p.http/body read-string)]
      {::iex/id    id
       ::iex/index index})))

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

(def abrams-plugin
  {::iex/plugin-id
   ::abrams-service

   ::iex/plugin-render-to-attr-left-menu
   (fn [{:abrams.diplomat.api/keys [attr-services-in attr-services-out]}]
     (let [services
           (-> (merge-with merge
                 (into {} (map #(vector % {:abrams.diplomat.api/service % ::service-in true})) attr-services-in)
                 (into {} (map #(vector % {:abrams.diplomat.api/service % ::service-out true})) attr-services-out))
               (vals))]
       (dom/div
         (if (seq services)
           (ui/panel {::ui/panel-title "Services"
                      ::ui/panel-tag   (count services)
                      ::ui/block-wrap? false}
             (for [{:keys  [abrams.diplomat.api/service]
                    ::keys [service-in service-out]} (sort-by :abrams.diplomat.api/service services)]
               (ui/panel-block {:react-key (pr-str service)}
                 (dom/div (ui/gc :.flex) (name service))
                 (if service-in
                   (ui/tag {:classes [:.is-family-code :.is-primary]
                            :style   {:marginLeft "4px"}
                            :title   "Input"}
                     "I"))
                 (if service-out
                   (ui/tag {:classes [:.is-family-code :.is-link]
                            :style   {:marginLeft "4px"}
                            :title   "Output"}
                     "O")))))))))

   ::iex/plugin-render-to-resolver-menu
   (fn [{:abrams.diplomat.api/keys [service endpoint]}]
     (dom/div
       (if service
         (ui/panel {::ui/panel-title "Service"}
           (dom/div (name service))))

       (if endpoint
         (ui/panel {::ui/panel-title "Endpoint"}
           (dom/div (str "/api/" endpoint))))))

   ::iex/plugin-render-to-mutation-view-left
   (fn [{:abrams.diplomat.api/keys [service endpoint]}]
     (dom/div
       (if service
         (ui/panel {::ui/panel-title "Service"}
           (dom/div (name service))))

       (if endpoint
         (ui/panel {::ui/panel-title "Endpoint"}
           (dom/div (str "/api/" endpoint))))))})

(ws/defcard index-explorer
  (pws/index-explorer-card {::p/parser           parser
                            ::pws/portal-options {::f.portal/computed
                                                  {::iex/plugins [abrams-plugin]}}}))

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

(ws/deftest test-attr-provides->tree
  (is (= (iex/attr-provides->tree {})
         {:children []}))
  (is (= (iex/attr-provides->tree {:simple #{'path}})
         {:children [{:key :simple ::pc/sym-set #{'path}}]}))
  (is (= (iex/attr-provides->tree {:simple #{'path}
                                   :multi  #{'path}})
         {:children [{:key :simple ::pc/sym-set #{'path}}
                              {:key :multi ::pc/sym-set #{'path}}]}))
  (is (= (iex/attr-provides->tree {[:simple :multi] #{'path}
                                   :simple          #{'path}})
         {:children
          [{:key      :simple ::pc/sym-set #{'path}
            :children [{:key :multi ::pc/sym-set #{'path}}]}]}))
  (is (= (iex/attr-provides->tree {[:simple :multi] #{'path}
                                   [:simple :multi :more] #{'path}
                                   :simple          #{'path}})
         {:children
          [{:key      :simple ::pc/sym-set #{'path}
            :children [{:key :multi ::pc/sym-set #{'path}
                        :children [{:key :more ::pc/sym-set #{'path}}]}]}]})))

(ws/deftest test-compute-nodes-links
  (is (= (iex/compute-nodes-links {::iex/attributes []})
         {:nodes [] :links []}))
  (is (= (iex/compute-nodes-links
           {::iex/attributes [{::pc/attribute :movie/name
                               ::iex/weight   1
                               ::iex/reach    1}]})
         {:nodes [{:attribute ":movie/name"
                   :multiNode false
                   :mainNode  false
                   :weight    1
                   :reach     1
                   :radius    3}]
          :links []}))
  (is (= (-> (iex/compute-nodes-links
               {::iex/attributes [{::pc/attribute      :movie/name
                                   ::pc/attr-reach-via {#{:movie/id} #{'movie-by-id}}
                                   ::iex/weight        1
                                   ::iex/reach         1}
                                  {::pc/attribute     :movie/id
                                   ::pc/attr-provides {:movie/name #{'movie-by-id}}
                                   ::iex/weight       1
                                   ::iex/reach        1}]})
             simple-compute-nodes-out)
         {:nodes [{:attribute ":movie/name"}
                  {:attribute ":movie/id"}]
          :links [{:source    ":movie/id"
                   :target    ":movie/name"
                   :weight    1
                   :resolvers "movie-by-id"
                   :deep      false}]}))
  (is (= (-> (iex/compute-nodes-links
               {::iex/nested-provides? true
                ::iex/attributes       [{::pc/attribute      :movie/name
                                         ::pc/attr-reach-via {[#{:movie/id} :movie/latest] #{'latest-movie}}
                                         ::iex/weight        1
                                         ::iex/reach         1}
                                        {::pc/attribute     :movie/id
                                         ::pc/attr-provides {[:movie/latest :movie/name] #{'latest-movie}}
                                         ::iex/weight       1
                                         ::iex/reach        1}]})
             simple-compute-nodes-out)
         {:nodes [{:attribute ":movie/name"}
                  {:attribute ":movie/id"}]
          :links [{:source    ":movie/id"
                   :weight    1
                   :resolvers "latest-movie"
                   :target    ":movie/name"
                   :deep      true}]})))

(ws/deftest test-out-all-attributes
  (is (= (iex/out-all-attributes (eql/query->ast []) #{}) #{}))
  (is (= (iex/out-all-attributes (eql/query->ast [:foo]) #{}) #{:foo}))
  (is (= (iex/out-all-attributes (eql/query->ast [:foo {:bar [:baz]}]) #{}) #{:foo :bar :baz})))

(ws/deftest test-build-search-index
  (is (= (iex/build-search-vector {})
         []))
  (is (= (iex/build-search-vector {::pc/index-resolvers {'foo {::pc/sym 'foo}}})
         [{::fuzzy/string     "foo"
           ::iex/search-value 'foo
           ::iex/search-type  ::iex/search-type-resolver}]))
  (is (= (iex/build-search-vector {::pc/index-resolvers  {'foo {::pc/sym 'foo}}
                                   ::pc/index-mutations  {'mutation {::pc/sym 'mutation}}
                                   ::pc/index-attributes {:customer/id {::pc/attribute :customer/id}}})
         [{::fuzzy/string     "foo"
           ::iex/search-value 'foo
           ::iex/search-type  ::iex/search-type-resolver}
          {::fuzzy/string     "mutation"
           ::iex/search-value 'mutation
           ::iex/search-type  ::iex/search-type-mutation}
          {::fuzzy/string     ":customer/id"
           ::iex/search-value :customer/id
           ::iex/search-type  ::iex/search-type-attribute}])))
