(ns com.wsscode.pathom.viz.aux.demo-parser
  (:require [cljs.core.async :as async]
            [com.wsscode.async.async-cljs :refer [go-promise <!]]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.connect.foreign :as pcf]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.sugar :as ps]))

(defonce indexes (atom {}))

(def registry
  [(pc/constantly-resolver :answer 42)

   (pc/resolver 'slow
     {::pc/output [:slow]}
     (fn [_ _]
       (go-promise
         (<! (async/timeout 300))
         {:slow "slow"})))

   (pc/constantly-resolver :pi js/Math.PI)
   (pc/constantly-resolver :tau (* js/Math.PI 2))
   (pc/single-attr-resolver :pi :tau #(* % 2))
   (pc/alias-resolver :foreign :foreign->local)

   ; region errors

   (pc/resolver 'error
     {::pc/output [:error]}
     (fn [_ _]
       (throw (ex-info "Sync Error" {:error "data"}))))

   (pc/resolver 'maybe-error-error
     {::pc/output [:maybe-error]}
     (fn [_ _]
       (throw (ex-info "Sync Error" {:error "data"}))))

   (pc/resolver 'maybe-error-success
     {::pc/output [:maybe-error]}
     (fn [_ _]
       {:maybe-error "value"}))

   (pc/resolver 'error-with-dep
     {::pc/input  #{:pi}
      ::pc/output [:error-with-dep]}
     (fn [_ _]
       (throw (ex-info "Sync Error" {:error "data"}))))

   (pc/single-attr-resolver :error :error-dep pr-str)

   (pc/single-attr-resolver :error-dep :error-dep-dep pr-str)

   (pc/resolver 'error-async
     {::pc/output [:error-async]}
     (fn [_ _]
       (go-promise
         (throw (ex-info "Async Error" {:error "data"})))))

   (pc/resolver 'multi-dep-error
     {::pc/input  #{:error-with-dep :answer}
      ::pc/output [:multi-dep-error]}
     (fn [_ _]
       {:multi-dep-error "foi"}))

   (pc/resolver 'foreign-error-dep
     {::pc/input  #{:foreign-error}
      ::pc/output [:foreign-error-dep]}
     (fn [_ _]
       {:foreign-error-dep "foi"}))

   ; endregion
   ])

(def parser
  (p/async-parser
    {::p/env     {::p/reader               [{:foo (constantly "bar")}
                                            p/map-reader
                                            pc/reader3
                                            pc/open-ident-reader
                                            p/env-placeholder-reader]
                  ::p/placeholder-prefixes #{">"}}
     ::p/mutate  pc/mutate-async
     ::p/plugins [(pc/connect-plugin {::pc/indexes  indexes
                                      ::pc/register registry})
                  (pcf/foreign-parser-plugin {::pcf/parsers [(ps/connect-serial-parser
                                                               [(pc/constantly-resolver :foreign "value")
                                                                (pc/constantly-resolver :foreign2 "second value")
                                                                (pc/resolver 'foreign-error
                                                                  {::pc/output [:foreign-error]}
                                                                  (fn [_ _]
                                                                    (throw (ex-info "Foreign Error" {:error "data"}))))])]})
                  p/error-handler-plugin
                  p/trace-plugin]}))
