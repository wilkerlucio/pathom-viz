(ns com.wsscode.pathom.viz.demo-connector
  (:require [com.wsscode.pathom.viz.ws-connector.core :as ws-conn]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.connect :as pc]))

(def registry
  [(pc/constantly-resolver :works "WORKS!!")])

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
  (def tracked-parser
    (ws-conn/connect-parser
      {::ws-conn/parser-id "tracked parser"}
      parser))

  (tracked-parser {} [:works]))
