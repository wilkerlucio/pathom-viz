(ns com.wsscode.pathom.viz.ui.context
  (:require
    ["react" :as react]
    [cljs.spec.alpha :as s]
    [clojure.spec.gen.alpha :as gen]
    [com.fulcrologic.fulcro.components :as fc]
    [goog.object :as gobj]))

(declare new-context)

(defn contains-js-keys [obj keys]
  (every? #(gobj/containsKey obj %) keys))

(s/def ::context
  (s/with-gen #(contains-js-keys % ["Consumer" "Provider"]) #(gen/return (new-context))))

(s/def ::value any?)

(defn child-as-fn
  "Provides interop with a React component that takes a render function as its
   child.
   If you pass props in, it applies the equivalent of the #js reader to them.
   You must convert any nested data structures to a JS obj if desired."
  ([Component render]
   (fc/create-element
     Component
     #js {:children
          (fn [& v]
            (apply render v))}
     []))
  ([Component props render]
   (fc/create-element
     Component
     (-> {:children
          (fn [& v]
            (apply render v))}
         (merge props)
         clj->js)
     [])))

(defn new-context
  "Creates a new React context"
  ([] (.createContext react))
  ([initial-value] (.createContext react initial-value)))

(defn provider
  "A component that serves as a provider for the provided context.
   (def my-context (create))
   (defn parent-component []
     (provider {:context my-context :value  \"initial state\"}
       (dom/div \"children\")))"
  [{::keys [context value]} & children]
  #_ :clj-kondo/ignore
  (fc/create-element
    (gobj/get context "Provider")
    #js {:value value}
    children))

(defn consumer
  "A component that serves as a consumer for the provided context.
   Takes a render function as it's second parameter that will be called with
   the context value, and should return hiccup syntax.
   (def my-context (create))
   (defn my-component []
    (consumer {:context my-context}
     (fn [context-state]
      (dom/div \"The state is: \" context-state))))"
  [{::keys [context]} render-fn]
  (child-as-fn
    (gobj/get context "Consumer")
    render-fn))
