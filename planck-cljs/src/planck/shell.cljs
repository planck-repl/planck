(ns planck.shell
  (:require [cljs.spec :as s]
            [planck.io :refer [as-file]]))

(def ^:dynamic *sh-dir* nil)
(def ^:dynamic *sh-env* nil)

(defn sh
  "Launches a sub-process with the supplied arguments.
  Options are
  :in      may be given followed by a string of one of the following formats:
           String conforming to URL Syntax: 'file:///tmp/test.txt'
           String pointing at an *existing* 'file: '/tmp/test.txt'
           String with string input: 'Printing input from stdin with funy chars like $@ &'
           to be fed to the sub-process's stdin.
  :in-enc  option may be given followed by a String, used as a character
           encoding name (for example \"UTF-8\" or \"ISO-8859-1\") to
           convert the input string specified by the :in option to the
           sub-process's stdin.  Defaults to UTF-8.
  :out-enc option may be given followed by a String. If a
           String is given, it will be used as a character encoding
           name (for example \"UTF-8\" or \"ISO-8859-1\") to convert
           the sub-process's stdout to a String which is returned.
  :env     override the process env with a map of String: String.
  :dir     override the process dir with a String or planck.io/File.
  if the command can be launched, sh returns a map of
    :exit => sub-process's exit code
    :out  => sub-process's stdout (as String)
    :err  => sub-process's stderr (String via platform default encoding),
  otherwise it throws an exception"
  [& args]
  (let [{:keys [cmd opts]} (s/conform ::sh-args args)]
    (when (nil? cmd)
      (throw (s/explain ::sh-args args)))
    (let [{:keys [in in-enc out-enc env dir]}
          (merge {:out-enc nil :in-enc nil :dir *sh-dir* :env *sh-env*}
            (into {} (map (comp (juxt :key :val) second) opts)))
          dir (and dir (:path (as-file dir)))
          [exit out err] (js->clj (js/PLANCK_SHELL_SH (clj->js cmd) in in-enc out-enc (clj->js (seq env)) dir))]
      (if (and (== -1 exit)
               (= "launch path not accessible" err))
        (throw (js/Error. err))
        {:exit exit
         :out  out
         :err  err}))))

(s/def ::valid-sh-dir? (s/or :string string? :file #(instance? planck.io/File %)))

(s/def ::string-string-map? (s/and map? (fn [m]
                                          (and (every? string? (keys m))
                                               (every? string? (vals m))))))

(s/def ::sh-opt (s/alt
                   :in      (s/cat :key #{:in}      :val string?)
                   :in-enc  (s/cat :key #{:in-enc}  :val string?)
                   :out-enc (s/cat :key #{:out-enc} :val string?)
                   :dir     (s/cat :key #{:dir}     :val ::valid-sh-dir?)
                   :env     (s/cat :key #{:env}     :val ::string-string-map?)))

(s/def ::sh-args (s/cat :cmd (s/+ string?) :opts (s/* ::sh-opt)))

(s/def ::exit integer?)
(s/def ::out string?)
(s/def ::err string?)

(s/fdef sh
  :args ::sh-args
  :ret (s/keys :req-un [::exit ::out ::err]))
