(ns com.wsscode.pathom.viz.local-parser
  "Combined local parser to use that fulfills the Pathom Viz remote demands."
  (:require [com.wsscode.pathom.viz.aux.demo-parser :as demo-parser]
            [com.wsscode.pathom.viz.client-parser :as cp]
            [com.wsscode.pathom.viz.query-editor :as query.editor]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.connect :as pc]))

(defonce client-parsers
  (atom {:singleton demo-parser/parser}))

(def registry
  [cp/registry query.editor/registry])

(def parser
  (p/async-parser
    {::p/env     {::p/reader               [p/map-reader
                                            pc/reader3
                                            pc/open-ident-reader
                                            p/env-placeholder-reader]
                  ::cp/parsers*            client-parsers
                  ::p/placeholder-prefixes #{">"}}
     ::p/mutate  pc/mutate-async
     ::p/plugins [(pc/connect-plugin {::pc/register registry})
                  (p/env-wrap-plugin #(merge {::cp/parsers @client-parsers} %))
                  p/error-handler-plugin
                  p/elide-special-outputs-plugin
                  p/trace-plugin]}))
