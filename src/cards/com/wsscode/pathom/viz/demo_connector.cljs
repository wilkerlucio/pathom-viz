(ns com.wsscode.pathom.viz.demo-connector
  (:require [com.wsscode.pathom.viz.ws-connector.core :as p.connector]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.connect :as pc]))

; you can use goog.defines on ClojureScript or env vars in Clojure
; the important part is to have a flag to decide when to connect the parser
(def CONNECT_PARSER? true)

(def registry
  [(pc/constantly-resolver :works "WORKS!!")])

(def parser
  (cond->> (p/parser
             {::p/env     {::p/reader               [p/map-reader
                                                     pc/reader3
                                                     pc/open-ident-reader
                                                     p/env-placeholder-reader]
                           ::p/placeholder-prefixes #{">"}}
              ::p/mutate  pc/mutate
              ::p/plugins [(pc/connect-plugin {::pc/register registry})
                           p/error-handler-plugin
                           p/trace-plugin]})
    CONNECT_PARSER?
    (p.connector/connect-parser
      {::p.connector/parser-id ::my-parser})))

(comment
  (parser {} [:works]))
