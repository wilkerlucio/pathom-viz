(ns com.wsscode.pathom.viz.aux.clojure-connect-demo-parser
  (:require [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.socket-io.client :as sio.client]
            [com.wsscode.socket-io.client-connectors.io-socket :as sio.impl]))

(def registry
  [(pc/constantly-resolver :foo "bar")])

(def parser
  (p/parser
    {::p/env     {::p/reader               [p/map-reader
                                            pc/reader3
                                            pc/open-ident-reader
                                            p/env-placeholder-reader]
                  ::p/placeholder-prefixes #{">"}}
     ::p/mutate  pc/mutate
     ::p/plugins [(pc/connect-plugin {::pc/register registry})
                  p/error-handler-plugin
                  p/trace-plugin]}))

(comment
  (let [config (sio.impl/socket-io-connector
                 {::sio.client/server-url "http://localhost:8238/"})
        client (sio.client/connect config)]))
