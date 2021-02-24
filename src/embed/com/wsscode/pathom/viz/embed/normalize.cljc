(ns com.wsscode.pathom.viz.embed.normalize
  "Functions for dealing with normalizing databases. In particular `tree->db`."
  (:require
    [com.fulcrologic.fulcro.algorithms.do-not-use :as util]
    [edn-query-language.core :as eql]))

(defn get-ident [query data]
  (if-let [f (some-> query meta ::ident-fn)]
    (f data)))

(defn has-ident? [query]
  (some-> query meta (contains? ::ident-fn)))

(defn- normalize* [query data refs union-seen transform]
  (let [data (if transform
               (transform query data)
               data)]
    (cond
      (= '[*] query) data

      ;; union case
      (map? query)
      (let [ident (get-ident query data)]
        (if-not (nil? ident)
          (vary-meta (normalize* (get query (first ident)) data refs union-seen transform)
            assoc ::tag (first ident)) ; FIXME: What is tag for?
          (throw (ex-info "Union components must have an ident" {}))))

      (vector? data) data ;; already normalized

      :else
      (loop [q (seq query) ret data]
        (if-not (nil? q)
          (let [expr (first q)]
            (if (util/join? expr)
              (let [[k sel] (util/join-entry expr)
                    recursive?  (util/recursion? sel)
                    union-entry (if (util/union? expr) sel union-seen)
                    sel         (if recursive?
                                  (if-not (nil? union-seen)
                                    union-seen
                                    query)
                                  sel)
                    v           (get data k)]
                (cond
                  ;; graph loop: db->tree leaves ident in place
                  (and recursive? (eql/ident? v)) (recur (next q) ret)
                  ;; normalize one
                  (map? v)
                  (let [x (normalize* sel v refs union-entry transform)]
                    (if-not (not (has-ident? sel))
                      (let [i (get-ident sel x)]
                        (swap! refs update-in [(first i) (second i)] merge x)
                        (recur (next q) (assoc ret k i)))
                      (recur (next q) (assoc ret k x))))

                  ;; normalize many
                  (and (vector? v) (not (eql/ident? v)) (not (eql/ident? (first v))))
                  (let [xs (into [] (map #(normalize* sel % refs union-entry transform)) v)]
                    (if-not (not (has-ident? sel))
                      (let [is (into [] (map #(get-ident sel %)) xs)]
                        (if (vector? sel)
                          (when-not (empty? is)
                            (swap! refs
                              (fn [refs]
                                (reduce (fn [m [i x]]
                                          (update-in m i merge x))
                                  refs (zipmap is xs)))))
                          ;; union case
                          (swap! refs
                            (fn [refs']
                              (reduce
                                (fn [ret [i x]]
                                  (update-in ret i merge x))
                                refs' (map vector is xs)))))
                        (recur (next q) (assoc ret k is)))
                      (recur (next q) (assoc ret k xs))))

                  ;; missing key
                  (nil? v)
                  (recur (next q) ret)

                  ;; can't handle
                  :else (recur (next q) (assoc ret k v))))
              (let [k (if (seq? expr) (first expr) expr)
                    v (get data k)]
                (if (nil? v)
                  (recur (next q) ret)
                  (recur (next q) (assoc ret k v))))))
          ret)))))

(defn tree->db
  "Given a component class or instance and a tree of data, use the component's
   query to transform the tree into the default database format. All nodes that
   can be mapped via Ident implementations wil be replaced with ident links. The
   original node data will be moved into tables indexed by ident. If merge-idents
   option is true, will return these tables in the result instead of as metadata."
  ([query data]
   (tree->db query data nil))
  ([query data transform]
   (let [refs (atom {})
         ret  (normalize* query data refs nil transform)]
     (with-meta ret {:refs @refs}))))

(defn with-ident [ident-kw query]
  (with-meta query
    {::ident-fn
     (fn [data]
       [ident-kw (get data ident-kw)])}))

(comment
  (get-ident (with-ident :foo [:foo :bar]) {:foo 2})
  (let [res (tree->db
              [{:item (with-ident :foo [:foo :bar])}]
              {:item {:foo 1 :bar 2}})]
    res)

  (let [res (tree->db
              (with-ident :foo [:foo :bar])
              {:foo 1 :bar 2})]
    [res
     (meta res)]))
