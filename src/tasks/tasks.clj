(ns tasks
  (:require
    [cheshire.core :as json]
    [clojure.java.shell :as shell]))

(defn package-version
  ([] (package-version "package.json"))
  ([path]
   (-> (slurp path)
       (json/parse-string true)
       (get :version))))

(defn git-tag-exists? [tag]
  (seq (:out (shell/sh "git" "tag" "-l" tag))))

(defn release-app
  "Release the app. The process is done by adding a tag and pushing it, them Github Actions do the release flow."
  []
  (let [tag (str "v" (package-version "shells/electron/package.json"))]
    (if (git-tag-exists? tag)
      (println "Release" tag "already exists")
      (do
        (println "Creating tag" tag "...")
        (shell/sh "git" "tag" "-a" tag "-m" tag)
        (shell/sh "git" "push" "--follow-tags")
        (println "Done")
        nil))))
