(ns com.wsscode.pathom.viz.helpers
  (:require ["react-draggable" :refer [DraggableCore]]
            ["react" :as react]
            ["d3" :as d3]
            [cljs.core.async :refer [go <!]]
            [cljs.reader :refer [read-string]]
            [cljs.spec.alpha :as s]
            [clojure.pprint]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.fulcrologic.fulcro.algorithms.tx-processing :as txn]
            [com.fulcrologic.fulcro.components :as fc]
            [com.fulcrologic.fulcro.mutations :as fm]
            [com.fulcrologic.guardrails.core :refer [>def >defn >fdef => | <- ?]]
            [com.wsscode.common.async-cljs :refer [<?maybe]]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.viz.lib.local-storage :as ls]
            [edn-query-language.core :as eql]
            [goog.object :as gobj]))

(>def ::path (s/coll-of keyword? :kind vector?))
(>def ::path-map "The tree of maps" (s/map-of ::path map?))
(>def ::node (s/keys :opt-un [::children]))
(>def ::children (s/coll-of ::node))

(defn pd [f]
  (fn [e]
    (.preventDefault e)
    (f e)))

(defn target-value [e] (gobj/getValueByKeys e "target" "value"))

(>defn keep-unamespaced [m]
  [map? => map?]
  (into {}
        (filter (comp simple-keyword? first))
        m))

(defn stringify-keyword-values [x]
  (walk/prewalk
    (fn [x]
      (cond
        (simple-keyword? x)
        (name x)

        (keyword x)
        (str x)

        :else
        x))
    x))

(defn pprint-str [x]
  (with-out-str
    (clojure.pprint/pprint x)))

(defn resolve-path
  "Walks a db path, when find an ident it resets the path to that ident. Use to realize paths of relations."
  [state path]
  (loop [[h & t] path
         new-path []]
    (if h
      (let [np (conj new-path h)
            c  (get-in state np)]
        (if (eql/ident? c)
          (recur t c)
          (recur t (conj new-path h))))
      new-path)))

(defn swap-in!
  "Like swap! but starts at the ref from `env`, adds in supplied `path` elements (resolving across idents if necessary).
   Finally runs an update-in on that resultant path with the given `args`.

   Roughly equivalent to:

   ```
   (swap! (:state env) update-in (resolve-path @state (into (:ref env) path)) args)
   ```

   with a small bit of additional sanity checking.
   "
  [{:keys [state ref]} path & args]
  (let [path (resolve-path @state (into ref path))]
    (if (and path (get-in @state path))
      (apply swap! state update-in path args)
      @state)))

(defn pprint [x]
  (with-out-str (cljs.pprint/pprint x)))

(defn map-vals [f m]
  (into {} (fn [[k v]] [k (f v)]) m))

(defn index-by [f coll]
  (persistent!
    (reduce
      (fn [ret x]
        (let [k (f x)]
          (assoc! ret k x)))
      (transient {}) coll)))

(fm/defmutation update-value [{:keys [key fn args]}]
  (action [{:keys [state ref]}]
    (swap! state update-in ref update key #(apply fn % args))))

(defn update-value!
  "Helper to call transaction to update some key from current component."
  [component key fn & args]
  (fc/transact! component [`(update-value {:key ~key :fn ~fn :args ~args})]))

(defn safe-read [s]
  (try
    (read-string {:readers {'error  identity
                            'object nil}
                  :default identity}
      (str/replace s "TaggedValue:" "TaggedValue"))
    (catch :default e
      (js/console.warn "Safe read failed to read." s e)
      nil)))

(defn safe-read-string
  [s]
  (try (read-string s) (catch :default _ nil)))

(defn toggle-set-item [set item]
  (if (contains? set item)
    (disj set item)
    (conj set item)))

(defn vector-compare [[value1 & rest1] [value2 & rest2]]
  (let [result (compare value1 value2)]
    (cond
      (not (zero? result)) result
      (nil? value1) 0
      :else (recur rest1 rest2))))

(defn remove-not-found [x]
  (p/transduce-maps
    (remove (fn [[_ v]] (contains? #{::p/not-found ::fc/not-found} v)))
    x))

(defn env-parser-response [env]
  (-> env :result :body
      (get 'com.wsscode.pathom.viz.client-parser/client-parser-mutation)
      :com.wsscode.pathom.viz.client-parser/client-parser-response))

(defn transform->css [{:keys [x y k] :as t}]
  (if (seq t)
    (str "translate(" x ", " y ") scale(" k ")")
    ""))

(>defn path-map->tree
  "Generate a tree structure from a map of maps to data. For example, the given structure:

  {[:a] {:any data}
   [:a :b] {:more data}
   [:c] {:key foo}}

   It will return:

   {:children [{:any data
                :children [{:more data}]}
               {:key foo}]}"
  [path-map]
  [::path-map => ::node]
  (let [{:keys [items]}
        (reduce
          (fn [{:keys [items index]} path]
            (if (> (count path) 1)
              (let [prev (subvec path 0 (dec (count path)))]
                {:items items
                 :index (update-in index [prev :children] (fnil conj [])
                          (get index path))})
              {:items (conj items (get index path))
               :index index}))
          {:items []
           :index path-map}
          (->> path-map
               (keys)
               (sort #(vector-compare %2 %))))]
    {:children items}))

(defn pathom-remote [parser]
  {:transmit! (fn transmit! [_ {::txn/keys [ast result-handler]}]
                (let [edn           (eql/ast->query ast)
                      ok-handler    (fn [result]
                                      (try
                                        (result-handler (assoc result :status-code 200))
                                        (catch :default e
                                          (js/console.error e "Result handler for remote failed with an exception."))))
                      error-handler (fn [error-result]
                                      (try
                                        (result-handler (merge error-result {:status-code 500}))
                                        (catch :default e
                                          (js/console.error e "Error handler for remote failed with an exception."))))]
                  (go
                    (try
                      (ok-handler {:transaction edn :body (<?maybe (parser {} edn))})
                      (catch :default e
                        (js/console.error "Pathom Remote error:" e)
                        (error-handler {:body e}))))))})

(defn wrap-effect [f]
  (fn []
    (let [res (f)]
      (if (fn? res)
        res
        js/undefined))))

(defn create-context
  "A simple wrapper around React/createContext."
  [default]
  (react/createContext default))

(defn context-provider
  "Helper to get the context provider from a React context."
  [Context]
  (gobj/get Context "Provider"))

(defn create-context-provider
  "Helper to create a context provider element from a react context.

  Example:

      ; define context
      (def ThemeContext (create-context light-theme))

      ; on some render
      (create-context-provider ThemeContext {:value dark-theme}
        (dom/div ...))"
  [Context props & children]
  (apply react/createElement (context-provider Context) (clj->js props) children))

(defn use-state
  "A simple wrapper around React/useState. Returns a cljs vector for easy destructuring.

  React docs: https://reactjs.org/docs/hooks-reference.html#usestate"
  [initial-value]
  (into-array (react/useState initial-value)))

(defn use-effect
  "A simple wrapper around React/useEffect.

  React docs: https://reactjs.org/docs/hooks-reference.html#useeffect"
  ([f]
   (react/useEffect f))
  ([f args]
   (react/useEffect f (to-array args))))

(defn use-context
  "A simple wrapper around React/useContext."
  [Context]
  (react/useContext Context))

(defn use-reducer
  "A simple wrapper around React/useReducer. Returns a cljs vector for easy destructuring

  React docs: https://reactjs.org/docs/hooks-reference.html#usecontext"
  ([reducer initial-arg]
   (into-array (react/useReducer reducer initial-arg)))
  ([reducer initial-arg init]
   (into-array (react/useReducer reducer initial-arg init))))

(defn use-callback
  "A simple wrapper around React/useCallback. Converts args to js array before send.

  Different from React, with a single argument this will use an empty js array as deps,
  making run only once. To recompute on every render use `nil` as `args.

  React docs: https://reactjs.org/docs/hooks-reference.html#usecallback"
  ([cb]
   (react/useCallback cb #js []))
  ([cb args]
   (if args
     (react/useCallback cb (to-array args))
     (react/useCallback cb))))

(defn use-memo
  "A simple wrapper around React/useMemo. Converts args to js array before send.

  React docs: https://reactjs.org/docs/hooks-reference.html#usememo"
  ([cb]
   (react/useMemo cb))
  ([cb args]
   (react/useMemo cb (to-array args))))

(defn use-ref
  "A simple wrapper around React/useRef.

  React docs: https://reactjs.org/docs/hooks-reference.html#useref"
  ([] (react/useRef nil))
  ([value] (react/useRef value)))

(defn ref-current
  "Helper to get current value from a React ref."
  [ref] (gobj/get ref "current"))

(defn use-imperative-handle
  "A simple wrapper around React/useImperativeHandle.

  React docs: https://reactjs.org/docs/hooks-reference.html#useimperativehandle"
  [ref f]
  (js/React.useImperativeHandle ref f))

(defn use-layout-effect
  "A simple wrapper around React/useLayoutEffect.

  React docs: https://reactjs.org/docs/hooks-reference.html#uselayouteffect"
  ([f]
   (react/useLayoutEffect (wrap-effect f)))
  ([f args]
   (react/useLayoutEffect (wrap-effect f) (to-array args))))

(defn use-debug-value
  "A simple wrapper around React/useDebugValue.

  React docs: https://reactjs.org/docs/hooks-reference.html#uselayouteffect"
  ([value]
   (react/useDebugValue value))
  ([value formatter]
   (react/useDebugValue value formatter)))

(deftype ReactFnState [value set-value!]
  IDeref
  (-deref [o] value)

  IFn
  (-invoke [o x] (set-value! x)))

(defn use-fstate [initial-value]
  (let [[value set-value!] (use-state initial-value)]
    (->ReactFnState value set-value!)))

(deftype ReactAtomState [value set-value!]
  IDeref
  (-deref [o] value)

  IReset
  (-reset! [o new-value] (doto new-value set-value!))

  ISwap
  (-swap! [a f] (set-value! (f value)))
  (-swap! [a f x] (set-value! (f value x)))
  (-swap! [a f x y] (set-value! (f value x y)))
  (-swap! [a f x y more] (set-value! (apply f value x y more))))

(defn use-atom-state [initial-value]
  (let [[value set-value!] (use-state initial-value)]
    (->ReactAtomState value set-value!)))

(defn use-persistent-state [store-key initial-value]
  (let [[value set-value!] (use-state (ls/get store-key initial-value))
        set-persistent! (fn [x]
                          (ls/set! store-key x)
                          (doto x set-value!))]
    (->ReactAtomState value set-persistent!)))

(defn use-application-state [store-key initial-value]
  (assert fc/*app*
    "Application state hooked used outside of a Fulcro app context")
  (let [app-state* (-> fc/*app* :com.fulcrologic.fulcro.application/state-atom)
        [value set-value!] (use-state (get-in @app-state* [::hook-state store-key] initial-value))
        set-state! (fn [x]
                     (swap! app-state* assoc-in [::hook-state store-key] x)
                     (doto x set-value!))]
    (->ReactAtomState value set-state!)))

(deftype FulcroComponentProp [comp prop]
  IDeref
  (-deref [o] (-> comp fc/props (get prop)))

  IReset
  (-reset! [o new-value] (fm/set-value! comp prop new-value) new-value)

  ISwap
  (-swap! [a f] (fm/set-value! comp prop (f @a)))
  (-swap! [a f x] (fm/set-value! comp prop (f @a x)))
  (-swap! [a f x y] (fm/set-value! comp prop (f @a x y)))
  (-swap! [a f x y more] (fm/set-value! comp prop (apply f @a x y more))))

(defn use-component-prop [comp prop]
  (use-memo (->FulcroComponentProp comp prop) [(-> comp fc/props (get prop))]))

;; hooks

(defn use-d3-zoom
  ([element-fn]
   (let [[svg-transform set-svg-transform!] (use-state {})]
     (use-effect #(let [svg        (.select d3 (element-fn))
                        apply-zoom (fn []
                                     (let [transform (.. d3 -event -transform)]
                                       (set-svg-transform!
                                         {:x (gobj/get transform "x")
                                          :y (gobj/get transform "y")
                                          :k (gobj/get transform "k")})))
                        zoom       (doto (.zoom d3)
                                     (.on "zoom" apply-zoom))]
                    (.call svg zoom)
                    js/undefined) [])
     svg-transform)))

(defn pathom-run-stats [x]
  (some-> x meta :com.wsscode.pathom3.connect.runner/run-stats))

(defn response-trace [x]
  (or (:com.wsscode.pathom/trace x)
      x))
