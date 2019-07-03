;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns ^:no-doc planck.from.cljs-bean.from.cljs.core)

;; Copied and made public, adding ^not-native hints
(defn -indexOf
  ([^not-native coll x]
   (-indexOf coll x 0))
  ([^not-native coll x start]
   (let [len (count coll)]
     (if (>= start len)
       -1
       (loop [idx (cond
                    (pos? start) start
                    (neg? start) (max 0 (+ start len))
                    :else start)]
         (if (< idx len)
           (if (= (nth coll idx) x)
             idx
             (recur (inc idx)))
           -1))))))

;; Copied and made public, adding ^not-native hints
(defn -lastIndexOf
  ([^not-native coll x]
   (-lastIndexOf coll x (count coll)))
  ([^not-native coll x start]
   (let [len (count coll)]
     (if (zero? len)
       -1
       (loop [idx (cond
                    (pos? start) (min (dec len) start)
                    (neg? start) (+ len start)
                    :else start)]
         (if (>= idx 0)
           (if (= (nth coll idx) x)
             idx
             (recur (dec idx)))
           -1))))))

;; Copied and made public, adding ^not-native hints
(defn compare-indexed
  "Compare indexed collection."
  ([^not-native xs ys]
   (let [xl (count xs)
         yl (count ys)]
     (cond
       (< xl yl) -1
       (> xl yl) 1
       (== xl 0) 0
       :else (compare-indexed xs ys xl 0))))
  ([^not-native xs ys len n]
   (let [d (compare (nth xs n) (nth ys n))]
     (if (and (zero? d) (< (+ n 1) len))
       (recur xs ys len (inc n))
       d))))

;; Copied and made public, adding ^not-native hint
(defn equiv-sequential
  "Assumes x is sequential. Returns true if x equals y, otherwise
  returns false."
  [^not-native x y]
  (boolean
    (when (sequential? y)
      (if (and (counted? x) (counted? y)
            (not (== (count x) (count y))))
        false
        (loop [xs (seq x) ys (seq y)]
          (cond (nil? xs) (nil? ys)
                (nil? ys) false
                (= (first xs) (first ys)) (recur (next xs) (next ys))
                :else false))))))

;; Copied and made public, adding ^not-native hints
(defn ci-reduce
  "Accepts any collection which satisfies the ICount and IIndexed protocols and
reduces them without incurring seq initialization"
  ([^not-native cicoll f]
   (let [cnt (-count cicoll)]
     (if (zero? cnt)
       (f)
       (loop [val (-nth cicoll 0), n 1]
         (if (< n cnt)
           (let [nval (f val (-nth cicoll n))]
             (if (reduced? nval)
               @nval
               (recur nval (inc n))))
           val)))))
  ([^not-native cicoll f val]
   (let [cnt (-count cicoll)]
     (loop [val val, n 0]
       (if (< n cnt)
         (let [nval (f val (-nth cicoll n))]
           (if (reduced? nval)
             @nval
             (recur nval (inc n))))
         val))))
  ([^not-native cicoll f val idx]
   (let [cnt (-count cicoll)]
     (loop [val val, n idx]
       (if (< n cnt)
         (let [nval (f val (-nth cicoll n))]
           (if (reduced? nval)
             @nval
             (recur nval (inc n))))
         val)))))

;; Copied from TransientArrayMap and modified with editable? param, adding ^not-native hint
(defn TransientArrayMap-conj! [^not-native tcoll o editable?]
  (if editable?
    (cond
      (map-entry? o)
      (-assoc! tcoll (key o) (val o))

      (vector? o)
      (-assoc! tcoll (o 0) (o 1))

      :else
      (loop [es (seq o) tcoll tcoll]
        (if-let [e (first es)]
          (recur (next es)
            (-assoc! tcoll (key e) (val e)))
          tcoll)))
    (throw (js/Error. "conj! after persistent!"))))

;; Copied from PersistentArrayMap, adding ^not-native hint
(defn PersistentArrayMap-conj [^not-native coll entry]
  (if (vector? entry)
    (-assoc coll (-nth entry 0) (-nth entry 1))
    (loop [ret coll es (seq entry)]
      (if (nil? es)
        ret
        (let [e (first es)]
          (if (vector? e)
            (recur (-assoc ret (-nth e 0) (-nth e 1))
              (next es))
            (throw (js/Error. "conj on a map takes map entries or seqables of map entries"))))))))

;; Copied from TransientVector and parameterized on type-name, adding ^not-native hint
(defn TransientVector-assoc! [^not-native tcoll key val type-name]
  (if (number? key)
    (-assoc-n! tcoll key val)
    (throw (js/Error. (str type-name "'s key for assoc! must be a number.")))))

;; Copied from PersistentVector and parameterized on type and cnt, adding ^not-native hint
(defn PersistentVector-equiv [^not-native coll other type cnt]
  (if (instance? type other)
    (if (== cnt (count other))
      (let [me-iter  (-iterator coll)
            you-iter (-iterator other)]
        (loop []
          (if ^boolean (.hasNext me-iter)
            (let [x (.next me-iter)
                  y (.next you-iter)]
              (if (= x y)
                (recur)
                false))
            true)))
      false)
    (equiv-sequential coll other)))

;; Copied from PersistentVector, adding ^not-native hint
(defn PersistentVector-lookup [^not-native coll k not-found]
  (if (number? k)
    (-nth coll k not-found)
    not-found))

;; Copied from PersistentVector, adding ^not-native hint
(defn PersistentVector-assoc [^not-native coll k v]
  (if (number? k)
    (-assoc-n coll k v)
    (throw (js/Error. "Vector's key for assoc must be a number."))))

;; Copied from PersistentVector an parameterized on cnt
(defn PersistentVector-contains-key? [coll k cnt]
  (if (integer? k)
    (and (<= 0 k) (< k cnt))
    false))
