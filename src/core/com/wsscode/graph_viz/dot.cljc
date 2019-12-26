(ns com.wsscode.graph-viz.dot
  (:require [com.fulcrologic.guardrails.core :refer [>def >defn >fdef => | <- ?]]
            [clojure.core.async :refer [go]]))

(>def ::shape #{:com.wsscode.graph-viz.dot.shape/box
                :com.wsscode.graph-viz.dot.shape/circle
                :com.wsscode.graph-viz.dot.shape/diamond
                :com.wsscode.graph-viz.dot.shape/doublecircle
                :com.wsscode.graph-viz.dot.shape/doubleoctagon
                :com.wsscode.graph-viz.dot.shape/egg
                :com.wsscode.graph-viz.dot.shape/ellipse
                :com.wsscode.graph-viz.dot.shape/hexagon
                :com.wsscode.graph-viz.dot.shape/house
                :com.wsscode.graph-viz.dot.shape/invhouse
                :com.wsscode.graph-viz.dot.shape/invtrapezium
                :com.wsscode.graph-viz.dot.shape/invtriangle
                :com.wsscode.graph-viz.dot.shape/Mcircle
                :com.wsscode.graph-viz.dot.shape/Mdiamond
                :com.wsscode.graph-viz.dot.shape/Mrecord
                :com.wsscode.graph-viz.dot.shape/Msquare
                :com.wsscode.graph-viz.dot.shape/none
                :com.wsscode.graph-viz.dot.shape/none
                :com.wsscode.graph-viz.dot.shape/octagon
                :com.wsscode.graph-viz.dot.shape/parallelogram
                :com.wsscode.graph-viz.dot.shape/plaintext
                :com.wsscode.graph-viz.dot.shape/plaintext
                :com.wsscode.graph-viz.dot.shape/point
                :com.wsscode.graph-viz.dot.shape/polygon
                :com.wsscode.graph-viz.dot.shape/record
                :com.wsscode.graph-viz.dot.shape/trapezium
                :com.wsscode.graph-viz.dot.shape/triangle
                :com.wsscode.graph-viz.dot.shape/tripleoctagon})

(>def ::arrow #{:com.wsscode.graph-viz.dot.arrow/box
                :com.wsscode.graph-viz.dot.arrow/crow
                :com.wsscode.graph-viz.dot.arrow/curve
                :com.wsscode.graph-viz.dot.arrow/diamond
                :com.wsscode.graph-viz.dot.arrow/dot
                :com.wsscode.graph-viz.dot.arrow/icurve
                :com.wsscode.graph-viz.dot.arrow/inv
                :com.wsscode.graph-viz.dot.arrow/none
                :com.wsscode.graph-viz.dot.arrow/normal
                :com.wsscode.graph-viz.dot.arrow/tee
                :com.wsscode.graph-viz.dot.arrow/vee})

{::name "G"
 ::type ::type-diagraph
 ::draw [{::size "4,4"}
         ["main" {::shape :com.wsscode.graph-viz.dot.shape/box}]
         ["main" "parse" {::weight 8}]
         ["parse" "execute"]
         ["main" "init" {::style "dotted"}]
         ["main" "cleanup"]
         ["execute" ["make_string" "printf"]]
         ["init" "make_string"]
         ["edge" {::color "red"}]
         ["main" "printf" {::style ::bold ::label "100 times"}]
         ["make-string" {::label "make a\nstring"}]
         ["node" {::shape "box" ::style "filled"}]
         ["execute" "compare"]]}
