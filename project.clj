(defproject com.wsscode/pathom-viz "1.1.0-SNAPSHOT"
  :description "A suite of visual components to support Pathom."
  :url "https://github.com/wilkerlucio/pathom-viz"
  :min-lein-version "2.7.0"
  :license {:name "MIT Public License"
            :url  "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.9.0" :scope "provided"]
                 [org.clojure/clojurescript "1.10.238" :scope "provided"]
                 [com.wsscode/async "1.0.2"]
                 [com.fulcrologic/guardrails "0.0.12"]
                 [com.wsscode/fuzzy "1.0.0"]]

  :source-paths ["src/core"]

  :jar-exclusions [#"workspaces/.*"])
