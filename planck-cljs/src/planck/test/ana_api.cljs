(ns planck.test.ana-api
  (:refer-clojure :exclude [find-ns ns-interns])
  (:require [cljs.env :as env]
            [cljs.analyzer :as ana]))

(defn resolve
  "Given an analysis environment resolve a var. Analogous to
   clojure.core/resolve"
  [env sym]
  {:pre [(map? env) (symbol? sym)]}
  (try
    (ana/resolve-var env sym
      (ana/confirm-var-exists-throw))
    (catch :default e
      (ana/resolve-macro-var env sym))))

(defn all-ns
  "Return all namespaces. Analagous to clojure.core/all-ns but
  returns symbols identifying namespaces not Namespace instances."
  ([]
   (all-ns env/*compiler*))
  ([state]
   (keys (get @state ::ana/namespaces))))

(defn find-ns
  "Given a namespace return the corresponding namespace analysis map. Analagous
  to clojure.core/find-ns."
  ([sym]
   (find-ns env/*compiler* sym))
  ([state sym]
   {:pre [(symbol? sym)]}
   (get-in @state [::ana/namespaces sym])))

(defn ns-interns
  "Given a namespace return all the var analysis maps. Analagous to
  clojure.core/ns-interns but returns var analysis maps not vars."
  ([ns]
   (ns-interns env/*compiler* ns))
  ([state ns]
   {:pre [(symbol? ns)]}
   (merge
     (get-in @state [::ana/namespaces ns :macros])
     (get-in @state [::ana/namespaces ns :defs]))))

(defn ns-resolve
  "Given a namespace and a symbol return the corresponding var analysis map.
  Analagous to clojure.core/ns-resolve but returns var analysis map not Var."
  ([ns sym]
   (ns-resolve env/*compiler* ns sym))
  ([state ns sym]
   {:pre [(symbol? ns) (symbol? sym)]}
   (get-in @state [::ana/namespaces ns :defs sym])))
