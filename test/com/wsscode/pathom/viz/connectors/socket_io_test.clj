(ns com.wsscode.pathom.viz.connectors.socket-io-test
  (:require [com.wsscode.socket-io.client-connectors.io-socket :as sio]
            [com.wsscode.pathom.viz.client :as pvc]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.connect :as pc]))

(def parser
  (p/parser
    {::p/env     {::p/reader               [p/map-reader
                                            pc/reader3
                                            pc/open-ident-reader
                                            p/env-placeholder-reader]
                  ::p/placeholder-prefixes #{">"}}
     ::p/mutate  pc/mutate
     ::p/plugins [(pc/connect-plugin {::pc/parser-id `parser
                                      ::pc/register  []})
                  p/error-handler-plugin
                  p/trace-plugin]}))

(comment
  (-> (sio/socket-io-connector {})
      (pvc/connect parser)))
