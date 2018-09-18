(defproject com.wsscode/pathom-viz "1.0.0"
  :description "A suite of visual components to support Pathom."
  :url "https://github.com/wilkerlucio/pathom-viz"
  :min-lein-version "2.7.0"
  :license {:name "MIT Public License"
            :url  "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.9.0" :scope "provided"]
                 [org.clojure/clojurescript "1.10.238" :scope "provided"]
                 [com.wsscode/pathom "2.2.0-beta6" :scope "provided"]]

  :source-paths ["src/core"]

  :jar-exclusions [#"workspaces/.*"])
