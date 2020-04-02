(ns com.wsscode.transit
  (:refer-clojure :exclude [read write])
  (:require [cognitect.transit :as t])
  #?(:clj (:import (java.io ByteArrayOutputStream ByteArrayInputStream)
                   (com.cognitect.transit WriteHandler))))

#?(:clj
   (deftype DefaultHandler []
     WriteHandler
     (tag [this v] "unknown")
     (rep [this v] (pr-str v)))
   :cljs
   (deftype DefaultHandler []
     Object
     (tag [this v] "unknown")
     (rep [this v] (pr-str v))))

(defn read [s]
  #?(:clj
     (let [in     (ByteArrayInputStream. (.getBytes s))
           reader (t/reader in :json)]
       (t/read reader))

     :cljs
     (let [reader (t/reader :json)]
       (t/read reader s))))

#?(:cljs
   (def cljs-write-handlers
     {"default" (DefaultHandler.)}))

(defn ^String write [x]
  #?(:clj
     (let [out    (ByteArrayOutputStream. 4096)
           writer (t/writer out :json {:default-handler (DefaultHandler.)})]
       (t/write writer x)
       (.toString out))

     :cljs
     (let [writer (t/writer :json {:handlers cljs-write-handlers})]
       (t/write writer x))))
