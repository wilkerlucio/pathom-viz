(ns com.wsscode.pathom.viz.codemirror6
  (:require-macros [com.wsscode.pathom.viz.embed.macros :refer [defc]])
  (:require
    ; ["lezer" :as lezer]
    ; ["lezer-generator" :as lg]
    ; ["lezer-tree" :as lz-tree]
    ;["@codemirror/closebrackets" :refer [closeBrackets]]
    ;[nextjournal.clojure-mode.extensions.close-brackets :as close-brackets]
    ;[nextjournal.clojure-mode.extensions.formatting :as format]
    ;[nextjournal.clojure-mode.extensions.selection-history :as sel-history]
    ;[nextjournal.clojure-mode.keymap :as keymap]
    ;[nextjournal.clojure-mode.live-grammar :as live-grammar]
    ;[nextjournal.clojure-mode.node :as n]
    ;[nextjournal.clojure-mode.selections :as sel]
    ;[nextjournal.clojure-mode.test-utils :as test-utils]
    ["@codemirror/autocomplete" :as autocomplete]
    ["@codemirror/fold" :as fold]
    ["@codemirror/gutter" :refer [lineNumbers]]
    ["@codemirror/highlight" :as highlight]
    ["@codemirror/history" :refer [history historyKeymap]]
    ["@codemirror/state" :refer [EditorState]]
    ["@codemirror/view" :as view :refer [EditorView]]
    [applied-science.js-interop :as j]
    [clojure.string :as str]
    [clojure.walk :as walk]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.guardrails.core :refer [<- => >def >defn >fdef ? |]]
    [com.wsscode.pathom.viz.helpers :as pvh]
    [helix.core :as h]
    [helix.hooks :as hooks]
    [nextjournal.clojure-mode :as cm-clj]
    [nextjournal.clojure-mode.node :as n]
    [nextjournal.clojure-mode.util :as u]))

(def theme
  (.theme EditorView
    (j/lit {:$content           {:white-space "pre-wrap"
                                 :padding     "10px 0"}
            :$$focused          {:outline "none"}
            :$line              {:padding     "0 9px"
                                 :line-height "1.6"
                                 :font-size   "16px"
                                 :font-family "var(--code-font)"}
            :$matchingBracket   {:border-bottom "1px solid var(--teal-color)"
                                 :color         "inherit"}
            :$gutters           {:background "transparent"
                                 :border     "none"}
            :$gutterElement     {:margin-left "5px"}
            ;; only show cursor when focused
            :$cursor            {:visibility "hidden"}
            "$$focused $cursor" {:visibility "visible"}})))

(defonce extensions #js[theme
                        (history)
                        highlight/defaultHighlightStyle
                        (view/drawSelection)
                        (lineNumbers)
                        (fold/foldGutter)
                        (.. EditorState -allowMultipleSelections (of true))
                        cm-clj/default-extensions
                        (.of view/keymap cm-clj/complete-keymap)
                        (.of view/keymap historyKeymap)
                        #_(.of (.-lineWrapping EditorView) "false")])

(defn make-extensions [{:keys [state readonly completion-words]
                        :or   {readonly false}}]
  (cond-> #js [extensions]
    completion-words
    (.concat #js [(autocomplete/autocompletion
                    #js {:activateOnTyping true
                         :override         #js [(autocomplete/completeFromList
                                                  (into-array (mapv pr-str completion-words)))]})])

    readonly
    (.concat #js [(.of (.-editable EditorView) "false")])

    state
    (.concat #js [(.of (.-updateListener EditorView)
                    (fn [^js v]
                      (let [str (.. v -state -doc toString)]
                        (if (not= str @state)
                          (state str)))))])))

(h/defnc Editor [{:keys [source props state]
                  :as   options}]
  (let [!view  (pvh/use-fstate nil)
        mount! (hooks/use-callback []
                 (fn [el]
                   (let [state (.create EditorState #js {:doc        (or (if state @state source) "")
                                                         :extensions (make-extensions options)})
                         ^js v (new EditorView
                                 (j/obj :state
                                   state
                                   :parent el
                                   :lineWrapping false
                                   :editable false))]
                     (!view v))))]

    (hooks/use-effect [source @!view]
      (when @!view
        (.setState @!view
          (.create EditorState #js {:doc        (or (if state @state source) "")
                                    :extensions (make-extensions options)}))))
    (hooks/use-effect [@!view] #(some-> @!view (j/call :destroy)))

    (dom/div
      {:classes (into ["flex-row" "flex-1" "overflow-auto" "whitespace-nowrap" "bg-white"]
                      (:classes props))
       :style   (:style props)
       :ref     mount!})))

(defn sorted-maps [x]
  (walk/postwalk
    (fn [x]
      (if (map? x)
        (try
          (into (sorted-map) x)
          (catch :default _ x))
        x))
    x))

(h/defnc EditorReadWrap [{:keys [source props]}]
  (let [source' (hooks/use-memo [(hash source)]
                  (cond
                    (string? source)
                    source

                    :else
                    (pvh/pprint-str (sorted-maps source))))]
    (h/$ Editor {:source   source'
                 :props    props
                 :readonly true})))

(defn clojure-read
  ([source] (clojure-read source {}))
  ([source props]
   (h/$ EditorReadWrap {:source source :props props})))

(defn clojure-entity-write
  [props]
  (h/$ Editor {:& props}))

(>def ::source string?)
(>def ::props map?)

(defc clojure-editor [{::keys [source props]}]
  (h/$ Editor {:source source
               :props  props}))
