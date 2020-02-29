(ns tangle.core
  (:require [clojure.string :as str]))

(def default-graph-options
  "Default options for a graph"
  {:dpi 100 :rankdir :TP})

(def default-node-options
  "Default options for a node"
  {})

(def default-edge-options
  "Default options for an edge"
  {})

(defn escape-cmap
  "Character map for escaping DOT values"
  [c]
  (when ((set "|:\"{}<>[]") c)
    (str "\\" c)))

(defn escape
  "Helper function for escaping strings to valid DOT values"
  [s]
  (str/escape s escape-cmap))

(defn wrap-brackets-if
  "Wrap brackets to x if t is not empty"
  [t x]
  (if-not (empty? t)
    (str "[" x "]")
    x))

(declare format-record)

(defn format-record-wrap
  "Recursively format record type labels changing direction"
  [x]
  (cond (nil? x) ""
        (string? x) x
        (sequential? x) (str "{"
                          (->> x
                               (map format-record-wrap)
                               (interpose "|")
                               (apply str))
                          "}")
        :else (pr-str x)))

(defn format-record
  "Recursively format record type labels without changing direction."
  [x]
  (cond (nil? x) ""
        (string? x) x
        (sequential? x) (->> x
                             (map format-record-wrap)
                             (interpose "|")
                             (apply str))
        :else (pr-str x)))

(defn format-label
  "Format label into DOT value.

  Handles regular Clojure data types.
  Sequential data are turned into record type labels.
  Hiccup-like data is turned into HTML-like labels."
  [x]
  (cond (nil? x) ""
        (string? x) x
        (sequential? x) (format-record x)
        :else (pr-str x)))

(defn format-id
  "Formats an id value in DOT format with proper escaping"
  [x]
  (cond
    (string? x) (str \" (escape x) \")
    (keyword? x) (str \" (some-> (namespace x) (str \/)) (name x) \")
    :else (str x)))

(defn format-option-value
  "Formats an option value in DOT format with proper escaping"
  [x]
  (cond
    (string? x) (if (= \< (first x)) ; HTML-labels
                  x
                  (str \" (escape x) \"))
    (keyword? x) (name x)
    (coll? x) (str "\""
                (->> x
                     (map format-option-value)
                     (interpose ",")
                     (apply str))
                "\"")
    :else (str x)))

(defn format-option
  "Formats a single option in DOT format"
  [[k v]]
  (str (name k) "=" (format-option-value v)))

(defn format-options
  "Formats a map of options in DOT format"
  [opts]
  (if (empty? opts) ""
                    (->> (if (:label opts)
                           (update-in opts [:label] format-label)
                           opts)
                         (map format-option)
                         (interpose ", ")
                         (apply str))))

(defn format-node
  "Formats the node as DOT node."
  [id options]
  (str (format-id id) (wrap-brackets-if options (format-options options))))

(defn format-edge
  "Formats the edge as DOT node."
  [src dst options directed?]
  (let [arrow (if directed? " -> " " -- ")]
    (str (format-id src) arrow (format-id dst)
      (wrap-brackets-if options (format-options options)))))

(defn map-edges [m]
  (mapcat (fn [[k vs]]
            (for [v vs] [k v]))
    m))

(defn graph->dot
  "Transforms a graph of nodes and edges into GraphViz DOT format"
  [nodes edges options]
  (let [directed? (:directed? options false)
        node->descriptor (:node->descriptor options (constantly nil))
        edge->descriptor (:edge->descriptor options (fn [n1 n2 opts] opts))
        node->id (:node->id options identity)
        node->cluster (:node->cluster options)
        cluster->parent (:cluster->parent options (constantly nil))
        cluster->id (:cluster->id options identity)
        cluster->descriptor (:cluster->descriptor options (constantly nil))

        current-cluster (::cluster options)
        cluster->nodes (if node->cluster
                         (group-by node->cluster nodes)
                         {nil nodes})
        clusters (keys cluster->nodes)
        full-clusters (loop [open clusters
                             result []]
                        (if (empty? open)
                          (sort (distinct result))
                          (recur (doall (distinct (remove nil? (map cluster->parent open))))
                            (concat result open))))
        clusters full-clusters]

    (apply str
      (cond current-cluster (str "subgraph cluster_" (if (empty? (cluster->id current-cluster)) "none" (cluster->id current-cluster)))
            directed? "digraph"
            :else "graph")
      (if (and (not current-cluster) (get-in options [:graph :label]))
        (str " \"" (escape (get-in options [:graph :label])) "\"")
        "")
      " {\n"

      (when current-cluster
        (let [cluster-options (cluster->descriptor current-cluster)]
          (apply str (interpose "\n" (map format-option cluster-options)))))

      "\n"

      (when-not current-cluster
        (let [graph-options (merge default-graph-options (:graph options))
              edge-options (merge default-node-options (:edge options))
              node-options (merge default-edge-options (:node options))]
          (str
            (when-not (empty? graph-options) (str "graph[" (format-options graph-options) "]\n"))
            (when-not (empty? node-options) (str "node[" (format-options node-options) "]\n"))
            (when-not (empty? edge-options) (str "edge[" (format-options edge-options) "]\n")))))

      ;; format nodes in current cluster
      (apply str (let [nodes-in-cluster (cluster->nodes current-cluster)]
                   (->> nodes-in-cluster
                        (map #(format-node (node->id %) (node->descriptor %)))
                        (interpose "\n"))))
      "\n"

      ;; format subclusters
      (let [clusters (->> clusters
                          (filter #(= (cluster->parent %) current-cluster))
                          (remove nil?))]
        (apply str (for [cluster clusters]
                     (graph->dot nodes [] (assoc options ::cluster cluster)))))

      "\n"

      (when-not current-cluster
        ;; format edges
        (apply str (->> edges
                        (map (fn [[src dst & opts]]
                               (if (empty? opts)
                                 (format-edge src dst (edge->descriptor src dst opts) (:directed? options))
                                 (format-edge src dst (edge->descriptor src dst (first opts)) (:directed? options)))))
                        (interpose "\n"))))
      "\n"

      ["}\n"])))

(defn remove-viewbox [svg]
  (str/replace svg #"viewBox=\"[^\"]+\"" ""))
