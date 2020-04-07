(ns com.wsscode.pathom.viz.parser-assistant
  (:require [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.fulcrologic.fulcro.algorithms.merge :as merge]
            [com.fulcrologic.fulcro.components :as fc]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [com.fulcrologic.fulcro.mutations :as fm]
            [com.wsscode.pathom.viz.client-parser :as cp]
            [com.wsscode.pathom.viz.index-explorer :as index.explorer]
            [com.wsscode.pathom.viz.query-editor :as query.editor]
            [com.wsscode.pathom.viz.ui.kit :as ui]
            [com.wsscode.pathom.viz.helpers :as pvh]))

(defn initialize-parser-assistent [this]
  (let [{::cp/keys [parser-id] :as props} (fc/props this)]
    (query.editor/load-indexes this {::query.editor/id (-> props :ui/query-editor ::query.editor/id)
                                     ::cp/parser-id    parser-id})
    (index.explorer/load-indexes this {::index.explorer/id (-> props :ui/index-explorer ::index.explorer/id)
                                       ::cp/parser-id      parser-id})))

(defn assoc-child [m path value]
  (if (map? (get-in m (butlast path)))
    (assoc-in m path value)))

(fc/defsc ParserAssistant
  [this {:ui/keys  [query-editor index-explorer]
         ::ui/keys [active-tab-id]}]
  {:pre-merge  (fn [{:keys [current-normalized data-tree]}]
                 (let [parser-id (or (::cp/parser-id data-tree)
                                     (::cp/parser-id current-normalized)
                                     ::singleton)]
                   (-> (merge {::assistant-id     (random-uuid)
                               ::cp/parser-id     parser-id
                               ::ui/active-tab-id ::query
                               :ui/query-editor   {}
                               :ui/index-explorer {}}
                         current-normalized data-tree)
                       (assoc-child [:ui/query-editor ::cp/parser-id] parser-id)
                       (assoc-child [:ui/index-explorer ::cp/parser-id] parser-id))))
   :ident      ::assistant-id
   :query      [::assistant-id
                ::ui/active-tab-id
                ::cp/parser-id
                {:ui/query-editor (fc/get-query query.editor/QueryEditor)}
                {:ui/index-explorer (fc/get-query index.explorer/IndexExplorer)}]
   :use-hooks? true}
  (pvh/use-effect #(initialize-parser-assistent this) [])

  (ui/tab-container {}
    (ui/tab-nav {:classes           [:.border-collapse-bottom]
                 ::ui/active-tab-id active-tab-id
                 ::ui/target        this}
      [{::ui/tab-id ::query} "Query"]
      [{::ui/tab-id ::index-explorer} "Index Explorer"])
    (case active-tab-id
      ::query
      (query.editor/query-editor query-editor)

      ::index-explorer
      (index.explorer/index-explorer index-explorer)

      (dom/div "Invalid page"))))

(def parser-assistant-ui (fc/factory ParserAssistant {:keyfn ::assistant-id}))

(defn select-parser
  "Select the browser tab. In case the parser isn't initialized it will merge a new
  component, otherwise just switch to the active one."
  [this p]
  (fm/set-value! this ::ui/active-tab-id p)
  (if (-> (fc/component->state-map this)
          (get ::assistant-id)
          (contains? p))
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
              ::manager-id]})))

(fc/defsc MultiParserManager
  [this {:ui/keys  [parser-assistant parser-url]
         ::cp/keys [available-parsers]
         ::ui/keys [active-tab-id]}]
  {:pre-merge  (fn [{:keys [current-normalized data-tree]}]
                 (let [parser-id (or (::cp/parser-id data-tree)
                                     (::cp/parser-id current-normalized)
                                     ::singleton)]
                   (merge {::manager-id           (random-uuid)
                           :ui/parser-url         ""
                           ::cp/parser-id         parser-id
                           ::cp/available-parsers #{}}
                     current-normalized data-tree)))
   :ident      ::manager-id
   :query      [::manager-id
                :ui/parser-url
                ::cp/available-parsers
                ::ui/active-tab-id
                {:ui/parser-assistant (fc/get-query ParserAssistant)}]
   :css        [[:.blank {:flex            "1"
                          :background      "#ccc"
                          :display         "flex"
                          :align-items     "center"
                          :justify-content "center"}]]
   :use-hooks? true}
  (pvh/use-effect #(reload-available-parsers this) [])

  (ui/column (ui/gc :.flex)
    #_ (ui/row {}
      (dom/input {:placeholder "http://localhost/graph"
                  :style       {:width "300px"}
                  :value       parser-url
                  :onChange    #(fm/set-string! this :ui/parser-url :event %)})
      (ui/button {:onClick #(do
                              (fc/transact! this [(add-parser-from-url {::cp/url parser-url})])
                              (reload-available-parsers this))}
        "Add parser from URL"))
    (ui/tab-container {}
      (ui/tab-nav {:classes             [(if parser-assistant :.border-collapse-bottom)]
                   ::ui/active-tab-id   active-tab-id
                   ::ui/tab-right-tools (ui/button {} "+")}
        (for [p available-parsers]
          [{::ui/tab-id       p
            ::ui/on-tab-close #(fc/transact! this [(remove-parser {::cp/parser-id p})])
            :onClick          #(select-parser this p)}
           (str p)]))

      (if parser-assistant
        (parser-assistant-ui parser-assistant)
        (dom/div :.blank "Select a parser")))))

(def multi-parser-manager (fc/factory MultiParserManager {:keyfn ::manager-id}))
