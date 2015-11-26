(ns planck.shell)

(def ^:dynamic *sh-dir* nil)
(def ^:dynamic *sh-env* nil)

(defn- parse-args
  [args]
  (let [default-encoding nil
        default-opts     {:out-enc default-encoding :in-enc default-encoding :dir *sh-dir* :env *sh-env*}
        [cmd opts] (split-with string? args)]
    [cmd (merge default-opts (apply hash-map opts))]))

(defn sh
  "Passes the given strings to Runtime.exec() to launch a sub-process.
  Options are
  :in      may be given followed by a string of one of the following formats:
           String conforming to URL Syntax: 'file:///tmp/test.txt'
           String pointing at an *existing* 'file: '/tmp/test.txt'
           String with string input: 'Printing input frmo stdin with funy chars like $@ &'
           to be fed to the sub-process's stdin.
  :in-enc  option may be given followed by a String, used as a character
           encoding name (for example \"UTF-8\" or \"ISO-8859-1\") to
           convert the input string specified by the :in option to the
           sub-process's stdin.  Defaults to UTF-8.
  :out-enc option may be given followed by :bytes or a String. If a
           String is given, it will be used as a character encoding
           name (for example \"UTF-8\" or \"ISO-8859-1\") to convert
           the sub-process's stdout to a String which is returned.
  :env     override the process env with a map of String: String.
  :dir     override the process dir with a String.
  sh returns a map of
    :exit => sub-process's exit code
    :out  => sub-process's stdout (as String)
    :err  => sub-process's stderr (String via platform default encoding)"
  [& args]
  (let [[cmd opts] (parse-args args)
        {:keys [in in-enc out-enc env dir]} opts]
    (let [[exit out err] (js->clj (js/PLANCK_SHELL_SH (clj->js cmd) in in-enc out-enc (clj->js (seq env)) dir))]
      {:exit exit
       :out  out
       :err  err})))
