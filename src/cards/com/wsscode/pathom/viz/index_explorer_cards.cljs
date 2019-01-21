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
            [nubank.workspaces.model :as wsm]))

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
