(ns tasks
  (:require
    [cheshire.core :as json]
    [clojure.java.shell :as shell]
    [clojure.tools.cli :refer [parse-opts]]))

(defn package-version
  ([] (package-version "package.json"))
  ([path]
   (-> (slurp path)
       (json/parse-string true)
       (get :version))))

(defn delete-tag
  ([] (delete-tag (package-version)))
  ([tag]
   (shell/sh "git" "tag" "-d" tag)
   (shell/sh "git" "push" "--delete" "origin" tag)))

(defn git-tag-exists? [tag]
  (seq (:out (shell/sh "git" "tag" "-l" tag))))

(def release-opts
  [["-f" "--force"]
   ["-h" "--help"]])

(defn release-app
  "Release the app. The process is done by adding a tag and pushing it, them Github Actions do the release flow."
  [& args]
  (let [tag   (str "v" (package-version "shells/electron/package.json"))
        send! (fn []
                (println "Creating tag" tag "...")
                (shell/sh "git" "tag" "-a" tag "-m" tag)
                (shell/sh "git" "push" "--follow-tags")
                (println "Done")
                nil)]
    (if (git-tag-exists? tag)
      (if (-> (parse-opts args release-opts) :options :force)
        (do
          (println "Forcing release, deleting previous tag" tag)
          (delete-tag tag)
          (send!))
        (println "Release" tag "already exists"))
      (send!))))
