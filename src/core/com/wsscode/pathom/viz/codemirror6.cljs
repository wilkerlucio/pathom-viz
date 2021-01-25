(ns com.wsscode.pathom.viz.codemirror6
  (:require ["@codemirror/closebrackets" :refer [closeBrackets]]
            ["@codemirror/fold" :as fold]
            ["@codemirror/gutter" :refer [lineNumbers]]
            ["@codemirror/highlight" :as highlight]
            ["@codemirror/history" :refer [history historyKeymap]]
            ["@codemirror/state" :refer [EditorState]]
            ["@codemirror/view" :as view :refer [EditorView]]
            ["lezer" :as lezer]
            ["lezer-generator" :as lg]
            ["lezer-tree" :as lz-tree]
            [applied-science.js-interop :as j]
            [clojure.string :as str]
            [nextjournal.clojure-mode :as cm-clj]
            [nextjournal.clojure-mode.extensions.close-brackets :as close-brackets]
            [nextjournal.clojure-mode.extensions.formatting :as format]
            [nextjournal.clojure-mode.extensions.selection-history :as sel-history]
            [nextjournal.clojure-mode.keymap :as keymap]
            [nextjournal.clojure-mode.live-grammar :as live-grammar]
            [nextjournal.clojure-mode.node :as n]
            [nextjournal.clojure-mode.selections :as sel]
            [nextjournal.clojure-mode.test-utils :as test-utils]
            [helix.hooks :as hooks]
            [helix.core :as h]
            [helix.dom :as dom]
            [com.wsscode.pathom.viz.helpers :as pvh]
            [clojure.walk :as walk]))

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

(h/defnc Editor [{:keys [source readonly]
                  :or   {readonly false}}]
  (let [!view  (pvh/use-fstate nil)
        mount! (hooks/use-callback []
                 (fn [el]
                   (let [state (.create EditorState #js {:doc        source
                                                         :extensions (if readonly
                                                                       #js [extensions
                                                                            (.of (.-editable EditorView) "false")]
                                                                       extensions)})
                         v     (new EditorView
                                 (j/obj :state
                                   state
                                   :parent el
                                   :lineWrapping false
                                   :editable false))]
                     (!view v))))]

    (hooks/use-effect [source]
      (when @!view
        (.setState @!view
          (.create EditorState #js {:doc        source
                                    :extensions (if readonly
                                                  #js [extensions
                                                       (.of (.-editable EditorView) "false")]
                                                  extensions)}))))
    (hooks/use-effect [@!view] #(some-> @!view (j/call :destroy)))

    (dom/div {:style {:flex       "1"
                      :display    "flex"
                      :overflow   "auto"
                      :whiteSpace "nowrap"}
              :ref   mount!})))

(defn sorted-maps [x]
  (walk/postwalk
    (fn [x]
      (if (map? x)
        (into (sorted-map) x)
        x))
    x))

(h/defnc EditorReadWrap [{:keys [source]}]
  (let [source' (hooks/use-memo [(hash source)]
                  (cond
                    (string? source)
                    source

                    :else
                    (pvh/pprint-str (sorted-maps source))))]
    (h/$ Editor {:source   source'
                 :readonly true})))

(defn clojure-read [source]
  (h/$ EditorReadWrap {:source source}))
