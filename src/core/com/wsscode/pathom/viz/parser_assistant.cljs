(ns com.wsscode.pathom.viz.parser-assistant
  (:require [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.fulcrologic.fulcro.algorithms.merge :as merge]
            [com.fulcrologic.fulcro.components :as fc]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [com.fulcrologic.fulcro.mutations :as fm]
            [com.wsscode.pathom.viz.client-parser :as cp]
            [com.wsscode.pathom.viz.ui.mutation-effects :as mf]
            [com.wsscode.async.async-cljs :refer [go-promise <?]]
            [com.wsscode.pathom.viz.index-explorer :as index-explorer]
            [com.wsscode.pathom.viz.query-editor :as query-editor]
            [com.wsscode.pathom.viz.request-history :as request-history]
            [com.wsscode.pathom.viz.ui.kit :as ui]
            [com.wsscode.pathom.viz.helpers :as h]
            [clojure.core.async :as async]))

(defn initialize-parser-assistant [this]
  (let [{::cp/keys [parser-id] :as props} (fc/props this)]
    (query-editor/load-indexes this {::query-editor/id (-> props :ui/query-editor ::query-editor/id)
                                     ::cp/parser-id    parser-id})
    (index-explorer/load-indexes this {::index-explorer/id (-> props :ui/index-explorer ::index-explorer/id)
                                       ::cp/parser-id      parser-id}))
  js/undefined)

(defn assoc-child [m path value]
  (if (map? (get-in m (butlast path)))
    (assoc-in m path value)))

(fc/defsc ParserAssistant
  [this {:ui/keys  [query-editor index-explorer request-history]
         ::ui/keys [active-tab-id]}]
  {:pre-merge  (fn [{:keys [current-normalized data-tree]}]
                 (let [parser-id (or (::cp/parser-id data-tree)
                                     (::cp/parser-id current-normalized)
                                     ::singleton)]
                   (-> (merge {::assistant-id      (random-uuid)
                               ::cp/parser-id      parser-id
                               ::ui/active-tab-id  ::tab-query
                               :ui/query-editor    {::query-editor/id parser-id}
                               :ui/index-explorer  {::index-explorer/id parser-id}
                               :ui/request-history {::request-history/id parser-id}}
                         current-normalized data-tree)
                       (assoc-child [:ui/query-editor ::cp/parser-id] parser-id)
                       (assoc-child [:ui/index-explorer ::cp/parser-id] parser-id))))
   :ident      ::assistant-id
   :query      [::assistant-id
                ::ui/active-tab-id
                ::cp/parser-id
                {:ui/query-editor (fc/get-query query-editor/QueryEditor)}
                {:ui/index-explorer (fc/get-query index-explorer/IndexExplorer)}
                {:ui/request-history (fc/get-query request-history/RequestHistory)}]
   :use-hooks? true}
  (h/use-effect #(initialize-parser-assistant this) [])

  (ui/tab-container {}
    (ui/tab-nav {:classes           [:.border-collapse-bottom]
                 ::ui/active-tab-id active-tab-id
                 ::ui/target        this}
      [{::ui/tab-id ::tab-query} "Query"]
      [{::ui/tab-id ::tab-index-explorer} "Index Explorer"]
      [{::ui/tab-id ::tab-requests} "Requests"])
    (case active-tab-id
      ::tab-query
      (query-editor/query-editor query-editor)

      ::tab-index-explorer
      (index-explorer/index-explorer index-explorer)

      ::tab-requests
      (request-history/request-history request-history)

      (dom/div "Invalid page"))))

(def parser-assistant-ui (fc/factory ParserAssistant {:keyfn ::assistant-id}))

(defn parser-exists? [this p]
  (-> (fc/component->state-map this)
      (get ::assistant-id)
      (contains? p)))

(defn initialize-assistant [this client-id]
  (merge/merge-component! (fc/any->app this) ParserAssistant
    {::assistant-id client-id
     ::cp/parser-id client-id}))

(defn select-parser
  "Select the browser tab. In case the parser isn't initialized it will merge a new
  component, otherwise just switch to the active one."
  [this p]
  (fm/set-value! this ::ui/active-tab-id p)
  (if (parser-exists? this p)
    (fm/set-value! this :ui/parser-assistant [::assistant-id p])
    (merge/merge-component! (fc/any->app this) ParserAssistant
      {::assistant-id p
       ::cp/parser-id p}
      :replace (conj (fc/get-ident this) :ui/parser-assistant))))

(fm/defmutation remove-parser [{::cp/keys [parser-id]}]
  (action [{:keys [state ref]}]
    (swap! state update-in (conj ref ::cp/available-parsers) disj parser-id)
    (if (= (second (get-in @state (conj ref :ui/parser-assistant)))
           parser-id)
      (swap! state update-in ref assoc :ui/parser-assistant nil)))
  (remote [{:keys [ast]}]
    (assoc ast :key `cp/remove-client-parser)))

(fm/defmutation add-parser-from-url [_]
  (action [{:keys [state ref]}]
    (swap! state update-in ref assoc :ui/parser-url ""))
  (remote [{:keys [ast]}]
    (assoc ast :key `cp/add-client-parser-from-url)))

(declare MultiParserManager)

(defn reload-available-parsers
  ([this] (reload-available-parsers this (fc/get-ident this)))
  ([app ref]
   (df/load! app ref MultiParserManager
     {:focus [::cp/available-parsers
              ::manager-id]})
   js/undefined))

(defn prompt [this message initial]
  (let [ch (async/promise-chan)]
    (fc/with-parent-context this
      (let [el (js/document.createElement "div")]
        (js/document.body.appendChild el)
        (js/ReactDOM.render
          (ui/prompt-modal {:prompt    message
                            :value     initial
                            :on-finish #(do
                                          (if % (async/put! ch %) (async/close! ch))
                                          (js/document.body.removeChild el))})
          el)))
    ch))

(defn add-from-url! [this]
  (go-promise
    (when-let [parser-url (<? (prompt this "Type the URL for the parser you want to add." "https://"))]
      (fc/transact! this [(add-parser-from-url {::cp/url parser-url})])
      (reload-available-parsers this))))

(fc/defsc MultiParserManager
  [this {:ui/keys  [parser-assistant]
         ::cp/keys [available-parsers]
         ::ui/keys [active-tab-id]}]
  {:pre-merge  (fn [{:keys [current-normalized data-tree]}]
                 (let [parser-id (or (::cp/parser-id data-tree)
                                     (::cp/parser-id current-normalized)
                                     ::singleton)]
                   (merge {::manager-id           (random-uuid)
                           ::cp/parser-id         parser-id
                           ::cp/available-parsers #{}}
                     current-normalized data-tree)))
   :ident      ::manager-id
   :query      [::manager-id
                ::cp/available-parsers
                ::ui/active-tab-id
                {:ui/parser-assistant (fc/get-query ParserAssistant)}]
   :css        [[:.blank {:flex            "1"
                          :background      "#ccc"
                          :display         "flex"
                          :align-items     "center"
                          :justify-content "center"}
                 ui/text-sans-13
                 [:.large {:font-size     "21px"
                           :margin-bottom "6px"}]]]
   :use-hooks? true}
  (let [reload       (h/use-callback #(reload-available-parsers this))
        add-from-url (h/use-callback #(add-from-url! this))]
    (h/use-effect reload [])

    (ui/column (ui/gc :.flex)
      (ui/tab-container {}
        (ui/tab-nav {:classes             [(if parser-assistant :.border-collapse-bottom)]
                     ::ui/active-tab-id   active-tab-id
                     ::ui/tab-right-tools (ui/button {:onClick add-from-url}
                                            "+")}
          (for [p available-parsers]
            [{::ui/tab-id       p
              ::ui/on-tab-close #(fc/transact! this [(remove-parser {::cp/parser-id p})])
              :onClick          #(select-parser this p)}
             (str p)]))

        (if parser-assistant
          (parser-assistant-ui parser-assistant)
          (dom/div :.blank
            (if (seq available-parsers)
              "Select a parser"
              (dom/div
                (dom/div :.large (ui/gc :.center) "Connect a parser")
                (dom/div "Not sure what to do to? "
                  (dom/a {:href    "#"
                          :onClick #(do
                                      (.preventDefault %)
                                      (fc/transact! this [(mf/open-external {:url "https://roamresearch.com/#/app/wsscode/page/RG9C93Sip"})]))}
                    "Check the docs")
                  ".")))))))))

(def multi-parser-manager (fc/computed-factory MultiParserManager {:keyfn ::manager-id}))
