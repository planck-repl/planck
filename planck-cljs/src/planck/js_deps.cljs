(ns planck.js-deps
  (:require
    [cljs.tools.reader :as r]))

(defonce ^:private foreign-libs-index (atom {}))

(defn- add-foreign-lib
  "Adds a foreign lib to an index."
  [index foreign-lib]
  (reduce (fn [index provides]
            (assoc index (symbol provides) foreign-lib))
    index
    (:provides foreign-lib)))

(defn- add-foreign-libs
  "Adds foreign libs in a vector of foreign libs to an index."
  [index foreign-libs]
  (reduce (fn [index foreign-lib]
            (add-foreign-lib index foreign-lib))
    index
    foreign-libs))

(defn index-foreign-libs
  "Indexes a compiler options map containing a :foreign-libs spec,
  swapping the result into the foreign libs index."
  [deps-map]
  (->> deps-map
    :foreign-libs
    (swap! foreign-libs-index add-foreign-libs)))

(defn index-upstream-foreign-libs
  "Indexes the upstream foreign libs deps."
  []
  (doseq [cljs-deps-source (js/PLANCK_LOAD_DEPS_CLJS_FILES)]
    (->> cljs-deps-source
      r/read-string
      index-foreign-libs)))

(defn topo-sorted-deps
  "Given a foreign libs index and a dep symbol to load,
  returns a topologically sorted sequence of deps to load,
  in load order."
  [index dep]
  {:pre [(symbol? dep)]}
  (let [spec (dep index)
        requires (map symbol (:requires spec))]
    (distinct (concat (mapcat #(topo-sorted-deps index %) requires) [dep]))))

(defn files-to-load*
  "Returns the files to load given and index and a foreign libs dep."
  [index dep]
  (map #(:file (% index)) (topo-sorted-deps index dep)))

(defn files-to-load
  "Returns the files to load for a given foreign libs dep."
  [dep]
  (files-to-load* @foreign-libs-index dep))
