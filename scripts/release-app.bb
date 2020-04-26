#!/usr/bin/env bb

(defn package-version
  ([] (package-version "package.json"))
  ([path]
   (-> (slurp path)
       (json/parse-string true)
       (get :version))))

(defn git-tag-exists? [tag]
  (seq (:out (shell/sh "git" "tag" "-l" tag))))

(let [tag (str "app-v" (package-version "shells/electron/package.json"))]
  (if (git-tag-exists? tag)
    (println "Release" tag "already exists")
    (do
      (println "Creating tag...")
      (shell/sh "git" "tag" "-a" tag "-m" tag)
      (shell/sh "git" "push" "--follow-tags"))))

