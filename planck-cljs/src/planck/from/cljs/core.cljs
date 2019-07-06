;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns ^:no-doc planck.from.cljs.core)

;; Extracted from cljs.core/pr-writer-impl
(defn date->str [obj]
  (let [normalize (fn [n len]
                    (loop [ns (str n)]
                      (if (< (count ns) len)
                        (recur (str "0" ns))
                        ns)))]
    (str
      (str (.getUTCFullYear obj)) "-"
      (normalize (inc (.getUTCMonth obj)) 2) "-"
      (normalize (.getUTCDate obj) 2) "T"
      (normalize (.getUTCHours obj) 2) ":"
      (normalize (.getUTCMinutes obj) 2) ":"
      (normalize (.getUTCSeconds obj) 2) "."
      (normalize (.getUTCMilliseconds obj) 3) "-"
      "00:00")))
