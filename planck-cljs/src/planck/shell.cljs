(ns planck.shell
  "Planck Shell capability."
  (:require-macros
   [planck.shell])
  (:require
   [cljs.spec.alpha :as s]
   [clojure.string :as string]
   [goog.object :as gobj]
   [planck.core]
   [planck.io :as io :refer [as-file]]
   [planck.repl :as repl]))

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
(gobj/set js/global "do_async_sh_callback" do-callback)

(defn- translate-result [js-res]
  (let [[exit out err] js-res]
    {:exit exit :out out :err err}))
(gobj/set js/global "translate_async_result" translate-result)

(defn- launch-fail-msg [executable-path]
  (str "Launch path \"" executable-path "\" not accessible."
    (when (string/includes? executable-path " ")
      (let [tokens (string/split executable-path #" ")]
        (str " Did you perhaps mean to launch using "
          (pr-str (first tokens)) ", with " (pr-str (rest tokens))
          " as arguments?")))))

(def ^:private nil-func (fn [_] nil))
(defn- sh-internal
  [& args]
  (let [{:keys [cmd opts cb]} (s/conform ::sh-async-args args)]
    (when (nil? cmd)
      (throw (s/explain ::sh-async-args args)))
    (when-not (s/valid? (s/nilable ::string-string-map?) *sh-env*)
      (throw (js/Error. (s/explain-str ::string-string-map? *sh-env*))))
    (let [{:keys [in in-enc out-enc env dir]}
          (merge {:out-enc nil :in-enc nil :dir (and *sh-dir* [:sh-dir *sh-dir*]) :env *sh-env*}
            (into {} (map (comp (juxt :key :val) second) opts)))
          dir        (and dir (:path (as-file (second dir))))
          async?     (not= cb nil-func)
          in-bytes   (when in
                       (let [acc (volatile! [])
                             os  (planck.core/->OutputStream
                                   (fn [bytes]
                                     (vswap! acc into bytes))
                                   (fn [])
                                   (fn []))]
                         (io/copy in os)
                         (into-array @acc)))
          translated (translate-result (js/PLANCK_SHELL_SH (clj->js cmd) in-bytes out-enc
                                         (clj->js (seq env)) dir (if async? (assoc-cb cb))))
          {:keys [exit err]} translated]
      (cond
        (or (== 126 exit)
            (== 127 exit))
        (throw (ex-info (if (empty? err)
                          (launch-fail-msg (first args))
                          (string/trimr err))
                 translated))

        :else
        (if async? nil translated)))))

(defn sh
  "Launches a sub-process with the supplied arguments.
  Parameters: cmd, <options>
  cmd      the command(s) (Strings) to execute. will be concatenated together.
  options  optional keyword arguments-- see below.
  Options are:
  `:in`      may be given followed by any legal input source for
             [[planck.io/copy]], e.g. [[planck.core/IInputStream]] or
             [[planck.core/IReader]] created using `planck.io`,
             [[planck.io/File]], or string, to be fed to the sub-process's stdin.
  `:in-enc`  option may be given followed by a String, used as a character
             encoding name (for example \"UTF-8\" or \"ISO-8859-1\") to
             convert the input string specified by the :in option to the
             sub-process's stdin.  Defaults to UTF-8.
  `:out-enc` option may be given followed by a String. If a
             String is given, it will be used as a character encoding
             name (for example \"UTF-8\" or \"ISO-8859-1\") to convert
             the sub-process's stdout to a String which is returned.
  `:env`     override the process env with a map of String: String.
  `:dir`     override the process dir with a String or [[planck.io/File]].
  if the command can be launched, sh returns a map of
    `:exit` => sub-process's exit code
    `:out`  => sub-process's stdout (as String)
    `:err`  => sub-process's stderr (String via platform default encoding),
  otherwise it throws an exception"
  [& args]
  (apply sh-internal (concat args [nil-func])))

(defn sh-async
  "Launches a sub-process with the supplied arguments.
  Parameters: `cmd`, <options>, `cb`
  `cmd`      the command(s) (Strings) to execute. will be concatenated together.
  `options`  optional keyword arguments-- see below.
  `cb`       the callback to call upon completion
  Options are:
  `:in`      may be given followed by any legal input source for
             [[planck.io/copy], e.g. [[planck.core/IInputStream]] or
             [[planck.core/IReader]] created using `planck.io`,
             [[planck.io/File]], or string, to be fed to the sub-process's stdin.
  `:in-enc`  option may be given followed by a String, used as a character
             encoding name (for example \"UTF-8\" or \"ISO-8859-1\") to
             convert the input string specified by the :in option to the
             sub-process's stdin.  Defaults to UTF-8.
  `:out-enc` option may be given followed by a String. If a
             String is given, it will be used as a character encoding
             name (for example \"UTF-8\" or \"ISO-8859-1\") to convert
             the sub-process's stdout to a String which is returned.
  `:env`     override the process env with a map of String: String.
  `:dir`     override the process dir with a String or planck.io/File.
  if the command can be launched, sh-async calls back with a map of
    `:exit` => sub-process's exit code
    `:out`  => sub-process's stdout (as String)
    `:err`  => sub-process's stderr (String via platform default encoding),
  Returns nil immediately"
  [& args]
  (apply sh-internal args))

(s/def ::string-string-map? (s/and map? (fn [m]
                                          (and (every? string? (keys m))
                                               (every? string? (vals m))))))

(s/def ::sh-opt
  (s/alt :in (s/cat :key #{:in} :val any?)
    :in-enc (s/cat :key #{:in-enc} :val string?)
    :out-enc (s/cat :key #{:out-enc} :val string?)
    :dir (s/cat :key #{:dir} :val (s/or :string string? :file io/file?))
    :env (s/cat :key #{:env} :val ::string-string-map?)))

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
