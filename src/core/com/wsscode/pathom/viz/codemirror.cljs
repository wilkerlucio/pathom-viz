(ns com.wsscode.pathom.viz.codemirror
  (:require [cljs.reader :refer [read-string]]
            [cljs.spec.alpha :as s]
            [clojure.string :as str]
            [cljsjs.codemirror]
            [com.wsscode.pathom.connect :as pc]
            [fulcro.client.dom :as dom]
            [fulcro.client.primitives :as fp]
            [goog.object :as gobj]

            ["codemirror/mode/clojure/clojure"]
            ["codemirror/addon/edit/matchbrackets"]
            ["codemirror/addon/edit/closebrackets"]
            ["codemirror/addon/fold/foldcode"]
            ["codemirror/addon/fold/foldgutter"]
            ["codemirror/addon/fold/brace-fold"]
            ["codemirror/addon/fold/indent-fold"]
            ["codemirror/addon/selection/active-line"]
            ["codemirror/addon/search/match-highlighter"]
            ["codemirror/addon/search/search"]
            ["codemirror/addon/search/searchcursor"]
            ["codemirror/addon/hint/anyword-hint"]
            ["codemirror/addon/hint/show-hint"]
            ["codemirror/addon/display/placeholder"]
            ["parinfer-codemirror" :as parinfer-cm]
            ["./pathom-mode"]))

(s/def ::mode (s/or :string string? :obj map?))
(s/def ::theme string?)
(s/def ::indentUnit pos-int?)
(s/def ::smartIndent boolean?)
(s/def ::lineNumbers boolean?)
(s/def ::readOnly boolean?)

(s/def ::value string?)
(s/def ::onChange (s/fspec :args (s/cat :code string?)))

(s/def ::options (s/keys :opt [::mode
                               ::theme
                               ::indentUnit
                               ::smartIndent
                               ::lineNumbers]))

(s/def ::props (s/keys :req-un [::value]
                 :opt [::options]))

(s/def ::extraKeys
  (s/map-of string? (s/or :str string? :fn fn?)))

(defn prop-call [comp name & args]
  (when-let [f (-> comp fp/props name)]
    (apply f args)))

(defn html-props [props]
  (->> props
       (remove (fn [[k _]] (namespace k)))
       (into {})
       (clj->js)))

(def pathom-cache (atom {}))

(declare autocomplete)

(fp/defui ^:once Editor
  Object
  (componentDidMount [this]
    (let [textarea   (gobj/get this "textNode")
          options    (-> this fp/props ::options (or {}) clj->js)
          process    (-> this fp/props ::process)
          codemirror (js/CodeMirror.fromTextArea textarea options)]
      (reset! pathom-cache {})

      (try
        (.on codemirror "change" #(when (not= (gobj/get % "origin") "setValue")
                                    (js/clearTimeout (gobj/get this "editorHold"))
                                    (gobj/set this "editorHold"
                                      (js/setTimeout
                                        (fn []
                                          (gobj/set this "editorHold" false))
                                        800))
                                    (prop-call this :onChange (.getValue %))))
        (.setValue codemirror (-> this fp/props :value))
        (if process (process codemirror))
        (catch :default e (js/console.warn "Error setting up CodeMirror" e)))
      (gobj/set this "codemirror" codemirror)))

  (componentWillReceiveProps [this {:keys     [value]
                                    ::pc/keys [indexes]}]
    (let [cm        (gobj/get this "codemirror")
          cur-index (gobj/getValueByKeys cm #js ["options" "pathomIndex"])]
      (when (and cur-index (not= indexes @cur-index))
        (reset! pathom-cache {})
        (reset! cur-index indexes)
        (gobj/set (gobj/getValueByKeys cm #js ["options" "hintOptions"]) "hint" (partial autocomplete indexes)))

      ; there is a race condition that happens when user types something, react updates state and try to update
      ; the state back to the editor, which moves the cursor in the editor in weird ways. the workaround is to
      ; stop accepting external values after a short period after user key strokes.
      (if-not (gobj/get this "editorHold")
        (let [cur-value (.getValue cm)]
          (if (and cm value (not= value cur-value))
            (.setValue cm value))))))

  (componentWillUnmount [this]
    (if-let [cm (gobj/get this "codemirror")]
      (.toTextArea cm)))

  (render [this]
    (let [props (fp/props this)]
      (dom/div (-> props (dissoc :value :onChange) (html-props))
        (js/React.createElement "textarea"
          #js {:ref          #(gobj/set this "textNode" %)
               :defaultValue (:value props)})))))

(def editor (fp/factory Editor))

(defn escape-re [input]
  (let [re (js/RegExp. "([.*+?^=!:${}()|[\\]\\/\\\\])" "g")]
    (-> input str (.replace re "\\$1"))))

(defn fuzzy-re [input]
  (-> (reduce (fn [s c] (str s (escape-re c) ".*")) "" input)
      (js/RegExp "i")))

(defn str->keyword [s] (keyword (subs s 1)))

(defn token-context [{::pc/keys [index-io] :as indexes} token]
  (let [state      (gobj/get token "state")
        mode       (gobj/get state "mode")
        path-stack (gobj/get state "pathStack")

        find-ctx   (fn find-ctx
                     ([s] (find-ctx s []))
                     ([s ctx]
                      (let [mode (gobj/get s "mode")
                            key  (gobj/get s "key")]
                        (cond
                          ; ident join: [{[:ident x] [|]}]
                          (and (= "join" mode)
                               (= "ident" (gobj/getValueByKeys s "key" "mode")))
                          (let [key (str->keyword (gobj/getValueByKeys s "key" "key"))]
                            {:type :attribute :context (conj ctx key)})

                          ; join: [{:child [|]}]
                          (and (= "join" mode)
                               (= (string? key)))
                          (let [key (str->keyword key)]
                            (if (contains? (get index-io #{}) key)
                              {:type :attribute :context (conj ctx key)}
                              (recur (gobj/getValueByKeys s "prev" "prev") (conj ctx key))))

                          :else
                          {:type :attribute :context ctx}))))]

    (cond
      (and (= "ident" mode)
           (or (nil? (gobj/get path-stack "key"))
               (= (gobj/get token "string") (gobj/get path-stack "key"))))
      {:type :ident}

      (and (= "join" mode)
           (or (= (gobj/get token "string") (gobj/get path-stack "key"))
               (nil? (gobj/get path-stack "key"))))
      (find-ctx (if (= "param-exp" (gobj/getValueByKeys path-stack "prev" "mode"))
                  (gobj/getValueByKeys path-stack "prev" "prev" "prev")
                  (gobj/getValueByKeys path-stack "prev" "prev")))

      (= "attr-list" mode)
      (if (gobj/getValueByKeys path-stack "prev" "mode")
        (find-ctx (gobj/get path-stack "prev"))
        ; no stack, empty context
        {:type :attribute :context []})

      (= "param-exp" mode)
      (let [prev (gobj/getValueByKeys path-stack "prev")]
        (recur indexes (js-obj "state" (js-obj "mode" (gobj/get prev "mode") "pathStack" prev)))))))

(defn ^:export completions [index token reg]
  (let [ctx (token-context index token)]
    (when reg
      (case (:type ctx)
        :attribute (->> (pc/discover-attrs (assoc index ::pc/cache pathom-cache)
                          (->> ctx :context (remove (comp #{">"} namespace)))))
        :ident (into {} (map #(hash-map % {})) (-> index ::pc/idents))
        {}))))

(gobj/set js/window "cljsDeref" deref)

(defn cm-completions [index cm]
  (let [cur   (.getCursor cm)
        ch    (.-ch cur)
        token (.getTokenAt cm cur)
        reg   (subs (.-string token) 0 (- ch (.-start token)))]
    (completions index token reg)))

(defn autocomplete [index cm options]
  (let [cur    (.getCursor cm)
        line   (.-line cur)
        ch     (.-ch cur)
        token  (.getTokenAt cm cur)
        reg    (subs (.-string token) 0 (- ch (.-start token)))
        blank? (#{"[" "{" " " "("} reg)
        start  (if blank? cur (-> js/CodeMirror (.Pos line (- ch (count reg)))))
        end    (if blank? cur (-> js/CodeMirror (.Pos line (gobj/get token "end"))))
        words  (->> (cm-completions index cm) (mapv first))]

    (if words
      (let [fuzzy (if blank? #".*" (fuzzy-re reg))]
        #js {:list (->> words
                        (remove (get index ::pc/autocomplete-ignore #{}))
                        (map str)
                        (filter #(re-find fuzzy %))
                        sort
                        clj->js)
             :from start
             :to   end}))))

(defn def-cm-command [name f]
  (gobj/set (gobj/get js/CodeMirror "commands") name f))

(defn ^:export key-has-children? [completions token]
  (let [reg (str->keyword (gobj/get token "string"))]
    (and (= "atom" (gobj/get token "type"))
         (or (seq (get completions reg))
             (= ">" (namespace reg))))))

(defn str-repeat [s n]
  (str/join (repeat n s)))

(def-cm-command "pathomJoin"
  (fn [cm]
    (let [cur    (.getCursor cm)
          token  (.getTokenAt cm cur)
          indent (or (gobj/getValueByKeys token #js ["state" "pathStack" "indent"])
                     0)]

      (if (and (= "attr-list" (gobj/getValueByKeys token #js ["state" "mode"]))
               (= "atom-composite" (gobj/get token "type")))
        (let [line  (.-line cur)
              start (.Pos js/CodeMirror line (gobj/get token "start"))
              end   (.Pos js/CodeMirror line (gobj/get token "end"))
              s     (gobj/get token "string")

              [cursor-end joined]
              (if (= (.-ch start) indent)
                [(.Pos js/CodeMirror (inc line) (+ 2 indent))
                 (str "{" s "\n" (str-repeat " " (inc indent)) "[]}")]

                [(.Pos js/CodeMirror line (+ (gobj/get token "start")
                                            (count s)
                                            3))
                 (str "{" s " []}")])]
          (.replaceRange cm joined start end)
          (.setCursor cm cursor-end)
          (.showHint cm))))))

(defn pathom [{::pc/keys [indexes] :as props}]
  (let [options {::lineNumbers               true
                 ::mode                      "pathom"
                 ::matchBrackets             true
                 ::autoCloseBrackets         true
                 ::highlightSelectionMatches true
                 ::foldGutter                true
                 ::hintOptions               {:hint           (partial autocomplete indexes)
                                              :completeSingle false}
                 ::extraKeys                 {"Ctrl-Space" "autocomplete"
                                              "Cmd-J"      "pathomJoin"}
                 ::gutters                   ["CodeMirror-linenumbers" "CodeMirror-foldgutter"]
                 :pathomIndex                (atom indexes)}]
    (editor (-> props
                (assoc ::process (fn [cm]
                                   (.on cm "keyup" (fn [cm e] (when (and (not (gobj/getValueByKeys cm #js ["state" "completionActive"]))
                                                                         (= 1 (-> (gobj/get e "key") (count))))
                                                                (js/CodeMirror.showHint cm))))
                                   (parinfer-cm/init cm "smart" #js {:forceBalance true})))
                (update ::options #(merge options %))))))

(defn clojure [props]
  (let [options {::lineNumbers               true
                 ::mode                      "clojure"
                 ::matchBrackets             true
                 ::highlightSelectionMatches true
                 ::foldGutter                true
                 ::gutters                   ["CodeMirror-linenumbers" "CodeMirror-foldgutter"]}]
    (editor (-> props
                (update ::options #(merge options %))))))
