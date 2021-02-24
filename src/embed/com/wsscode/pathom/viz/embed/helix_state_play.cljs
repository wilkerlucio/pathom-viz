(ns com.wsscode.pathom.viz.embed.helix-state-play
  (:require-macros
    [com.wsscode.pathom.viz.embed.macros :refer [defc]])
  (:require
    ["react" :as react]
    ["react-dom" :as react-dom]
    [autonormal.core :as an]
    [cljs.reader :refer [read-string]]
    [com.fulcrologic.fulcro.algorithms.denormalize :as denorm]
    [com.fulcrologic.fulcro.algorithms.normalize :as normalize]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.wsscode.misc.coll :as coll]
    [com.wsscode.pathom.viz.embed.messaging :as p.viz.msg]
    [com.wsscode.pathom.viz.lib.hooks :as p.hooks]
    [com.wsscode.pathom.viz.trace :as pvt]
    [com.wsscode.pathom.viz.trace-with-plan :as trace+plan]
    [com.wsscode.pathom.viz.ws-connector.pathom3 :as p.connector]
    [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
    [com.wsscode.pathom3.connect.indexes :as pci]
    [com.wsscode.pathom3.connect.operation :as pco]
    [com.wsscode.pathom3.connect.planner :as pcp]
    [com.wsscode.pathom3.interface.eql :as p.eql]
    [com.wsscode.tailwind-garden.core :as tailwind]
    [helix.hooks :as hooks]))

(defn provider
  [{:keys [context value]} & children]
  (apply react/createElement
    (.-Provider context)
    #js {:value value}
    children))

(defn create-app [config]
  (merge
    {::state*    (atom {:foo "state here"})
     ::listeners (atom {})}
    config))

(defn normalize-props [props classes]
  (update props :classes #(into classes %)))

(defn styled-component [component classes]
  (fn styled-component-internal
    ([props]
     (component (normalize-props props classes)))
    ([props child]
     (component (normalize-props props classes) child))
    ([props c1 c2]
     (component (normalize-props props classes) c1 c2))
    ([props c1 c2 c3]
     (component (normalize-props props classes) c1 c2 c3))
    ([props c1 c2 c3 c4]
     (component (normalize-props props classes) c1 c2 c3 c4))
    ([props c1 c2 c3 c4 & children]
     (apply component (normalize-props props classes) c1 c2 c3 c4 children))))

(def app-context (react/createContext nil))

(defn app-provider [app & children]
  (apply provider {:context app-context :value app} children))

(def button
  (styled-component dom/button
    ["border"
     "border-indigo-500"
     "bg-indigo-500"
     "font-normal"
     "font-sans"
     "text-white"
     "text-sm"
     "rounded-md"
     "px-4"
     "py-2"
     "transition"
     "duration-500"
     "ease"
     "select-none"
     "hover:bg-indigo-600"
     "focus:outline-none"
     "focus:shadow-outline"]))

(pco/defresolver all-users []
  {:user/all
   [{:user/id 123}
    {:user/id 1234}]})

(defonce plan-cache* (atom {}))

(def env
  (-> (pci/register
        [all-users
         (pbir/constantly-resolver :foo "bar")
         (pbir/static-table-resolver :user/id
           {123  {:user/id    123
                  :user/name  "Nomade"
                  :user/items [{:item/id 1}
                               {:item/id 2}]}
            1234 {:user/id    1234
                  :user/name  "Outro"
                  :user/items [{:item/id 1}]}})
         (pbir/static-table-resolver :item/id
           {1 {:item/id     1
               :item/number 10}
            2 {:item/id     2
               :item/number 12}})])
      (pcp/with-plan-cache plan-cache*)
      (p.connector/connect-env {:com.wsscode.pathom.viz.ws-connector.core/parser-id :app
                                ::p.connector/async?                                false})))

(defn pull-entity [db ref query]
  (get (an/pull db [{ref query}]) ref))

(defn call-remote [app data tx]
  ((::remote app) data tx))

(defn use-entity-state
  ([ident query] (use-entity-state ident {} query (hooks/use-context app-context)))
  ([ident props query] (use-entity-state ident props query (hooks/use-context app-context)))
  ([ident props query app]
   (let [state*  (-> app ::state*)
         query   (cond-> query
                   (keyword? ident) (conj ident))
         ident   (if (keyword? ident) [ident (get props ident)] ident)
         !local  (p.hooks/use-fstate
                   (merge (pull-entity @state* ident query) props))
         load    (some-> props meta ::options ::load)
         refresh (hooks/use-callback []
                   (fn refresh []
                     (!local (pull-entity @state* ident query))))]

     (hooks/use-effect []
       (swap! state* an/add props))

     (hooks/use-effect []
       (swap! (::listeners app) update ident coll/sconj refresh)
       #(swap! (::listeners app) update ident disj refresh))

     (hooks/use-effect [(hash ident) (hash query) (hash load)]
       (when load
         (let [res (call-remote app @!local query)]
           (swap! state* an/add res)
           (refresh))))

     (vary-meta @!local assoc ::app app ::refresh refresh ::ident ident))))

(defn get-app [state-or-app]
  (cond
    (::state* state-or-app)
    state-or-app

    (some-> state-or-app meta ::app)
    (-> state-or-app meta ::app)))

(defn trigger-listeners-refresh! [listeners]
  (doseq [f listeners] (f)))

(defn update-state [state-or-app f]
  (if-let [{::keys [state* listeners]} (get-app state-or-app)]
    (let [{::keys [refresh ident]} (some-> state-or-app meta)]
      (if ident
        (swap! state* update-in ident f)
        (swap! state* f))

      (trigger-listeners-refresh! (get @listeners ident))

      (if refresh (refresh)))

    (js/console.warn "Can't get the app to update")))

(defn with-ident [ident query]
  (-> query
      (conj ident)
      (vary-meta assoc :db/ident ident)))

(defn with-load [props]
  (vary-meta props assoc-in [::options ::load] true))

; region app code

(defc user [props]
  (let [{:user/keys [id name items] :as props'}
        (use-entity-state :user/id props
          [:user/name
           {:user/items [:item/id :item/number]}
           ;{:user/items (entity-query item)}
           ])]
    (dom/div {:key (str id)}
      (button {:onClick #(update-state props'
                           (fn [x]
                             (assoc x :user/name (str name " bla"))))} "Set data")
      (dom/div "User")
      (dom/div "ID: " id)
      (dom/div "Name: " name)
      (dom/div "Items: " (pr-str items)))))

(defonce app (create-app {::remote #(p.eql/process env % %2)}))

(defc users-comp [props]
  (let [{:keys [user/all] :as props'}
        (use-entity-state [::singleton ::app] props [])]
    (js/console.log "!! ALL" all)
    (dom/div {:className "mx-auto mt-6"}
      (connect props' :user/all user))))

(defc state-app [props]
  (or
    (p.hooks/use-garden-css (tailwind/everything))
    (app-provider app
      (dom/div
        (user (with-load {:user/id 123}))
        (users-comp (with-load props))))))

(defn start []
  (react-dom/render (state-app (with-load {})) (js/document.getElementById "app")))

; endregion

(comment
  (-> (an/db [{:user/id    123
               :user/name  "Nomade"
               :user/items [{:item/id 1}
                            {:item/id 2}]}])
      (an/add {:item/id 3}))

  (conj {} (#'an/lookup-ref-of {:user/id    123
                                :user/name  "Nomade"
                                :user/items [{:item/id 1}
                                             {:item/id 2}]}))

  (#'an/lookup-ref-of ^{:db/ident :foo} {:foo "bar"})

  app)
