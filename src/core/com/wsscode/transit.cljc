(ns com.wsscode.transit
  (:refer-clojure :exclude [read write])
  (:require [cognitect.transit :as t])
  #?(:clj (:import (java.io ByteArrayOutputStream ByteArrayInputStream))))

(defn read [s]
  #?(:clj
     (let [in     (ByteArrayInputStream. (.getBytes s))
           reader (t/reader in :json)]
       (t/read reader))

     :cljs
     (let [reader (t/reader :json)]
       (t/read reader s))))

(defn ^String write [x]
  #?(:clj
     (let [out    (ByteArrayOutputStream. 4096)
           writer (t/writer out :json)]
       (t/write writer x)
       (.toString out))

     :cljs
     (let [writer (t/writer :json)]
       (t/write writer x))))
