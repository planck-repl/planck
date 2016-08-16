(ns planck.shell
  (:require-macros planck.shell)
  (:require [cljs.spec :as s]
            [clojure.string]
            [planck.io :refer [as-file]]))

(def ^:dynamic *sh-dir* nil)
(def ^:dynamic *sh-env* nil)

(def ^:private cb-idx (atom 0))
(def ^:private callbacks (atom {}))
(defn- assoc-cb [cb]
  (let [idx (swap! cb-idx inc)]
    (swap! callbacks assoc idx cb)
    idx))
(defn- do-callback [idx]
  (this-as this ((@callbacks idx) this))
  (swap! callbacks dissoc idx))
(aset js/global "do_async_sh_callback" do-callback)

(defn- translate-result [js-res]
  (let [[exit out err] (js->clj js-res)]
    {:exit exit :out out :err err}))
(aset js/global "translate_async_result" translate-result)

(def ^:private launch-fail "launch path not accessible")

(def ^:private nil-func (fn [_] nil))
(defn- sh-internal
  [& args]
  (let [{:keys [cmd opts cb]} (s/conform ::sh-async-args args)]
    (when (nil? cmd)
      (throw (s/explain ::sh-async-args args)))
    (let [{:keys [in in-enc out-enc env dir]}
          (merge {:out-enc nil :in-enc nil :dir (and *sh-dir* [:sh-dir *sh-dir*]) :env *sh-env*}
            (into {} (map (comp (juxt :key :val) second) opts)))
          dir (and dir (:path (as-file (second dir))))
          async? (not= cb nil-func)
          translated (translate-result (js/PLANCK_SHELL_SH (clj->js cmd) in in-enc out-enc
                                                           (clj->js (seq env)) dir (if async? (assoc-cb cb))))
          {:keys [exit err]} translated]
      (if (or (== 126 exit)
              (== 127 exit)
              (and (== -1 exit)
                   (= launch-fail err)))
        (throw (js/Error. launch-fail))
        (if async? nil translated)))))

(defn sh
  "Launches a sub-process with the supplied arguments.
  Parameters: cmd, <options>
  cmd      the command(s) (Strings) to execute. will be concatenated together.
  options  optional keyword arguments-- see below.
  Options are:
  :in      may be given followed by a string of one of the following formats:
           String conforming to URL Syntax: 'file:///tmp/test.txt'
           String pointing at an *existing* 'file: '/tmp/test.txt'
           String with string input: 'Printing input from stdin with funny chars like $@ &'
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
  (apply sh-internal (concat args [nil-func])))

(when-not (clojure.string/starts-with? js/PLANCK_VERSION "1.")
  (defn sh-async 
    "Launches a sub-process with the supplied arguments.
    Parameters: cmd, <options>, cb
    cmd      the command(s) (Strings) to execute. will be concatenated together.
    options  optional keyword arguments-- see below.
    cb       the callback to call upon completion
    Options are:
    :in      may be given followed by a string of one of the following formats:
             String conforming to URL Syntax: 'file:///tmp/test.txt'
             String pointing at an *existing* 'file: '/tmp/test.txt'
             String with string input: 'Printing input from stdin with funny chars like $@ &'
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
    if the command can be launched, sh-async calls back with a map of
      :exit => sub-process's exit code
      :out  => sub-process's stdout (as String)
      :err  => sub-process's stderr (String via platform default encoding),
    Returns nil immediately"
    [& args]
    (apply sh-internal args)))

(s/def ::string-string-map? (s/and map? (fn [m]
                                          (and (every? string? (keys m))
                                               (every? string? (vals m))))))

(s/def ::sh-opt
  (s/alt :in      (s/cat :key #{:in}      :val string?)
         :in-enc  (s/cat :key #{:in-enc}  :val string?)
         :out-enc (s/cat :key #{:out-enc} :val string?)
         :dir     (s/cat :key #{:dir}     :val :planck.io/coercible-file?)
         :env     (s/cat :key #{:env}     :val ::string-string-map?)))

(s/def ::sh-args (s/cat :cmd (s/+ string?) :opts (s/* ::sh-opt)))
(s/def ::sh-async-args (s/cat :cmd (s/+ string?) :opts (s/* ::sh-opt) :cb fn?))

(s/def ::exit integer?)
(s/def ::out string?)
(s/def ::err string?)

(s/fdef sh
  :args ::sh-args
  :ret (s/keys :req-un [::exit ::out ::err]))

(s/fdef sh-async
  :args ::sh-async-args
  :ret nil?)
