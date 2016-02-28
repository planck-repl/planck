## Planck Namespaces

<img width="100" align="right" style="margin: 0ex 1em" src="img/planck-namespaces.jpg">
In order to make Planck more useful for doing actual work and interacting with your computer, some native I/O facilities have been added to the JavaScriptCore instance running in Planck and these have been exposed via a few namespaces. To make things easier to use, the functions in these namespaces adhere fairly closely to existing Clojure / ClojureScript analogs.

The code for these namespaces is included directly _in the Planck binary_, so they are always available—you just need to `require` them.

These namespaces comprise
* `planck.core`
* `planck.io`
* `planck.shell`

To explore these namespaces, you can evaluate `(dir planck.core)`, for example, to see the symbols in `planck.core`, and then use the `doc` macro to see the docs for any of the symbols.

### `planck.core`

This namespace includes basic I/O capabilities like `slurp`, `spit` and `read-line`. The I/O facilities are expressed in protocols defined in `planck.core` modeled after those in Clojure, like `IReader`, `IOutputStream`, _etc._, and these capabilities cooperate with facilities defined in `planck.io`.

Planck core also hosts some dynamic vars of interest like `*comand-line-args*`, `*in*`, `*out*`.

The `planck.core/file-seq` function imitates `clojure.core/file-seq`.

If you need to prompt for a password, take a look at `planck.core/read-password`.

The `planck.core` defines `eval` and other dynamic functions like `resolve`, `ns-resolve`, and `intern`.

Additionally, `planck.core/exit` is a function that takes an integer `exit-value` argument, so you can cause a Planck script to exit with any desired Unix exit value.

### `planck.io`

This namespace defines a lot of the `IOFactory` machinery, imitating `clojure.java.io`.

Additionally, filesystem facilities like `file`, `delete-file`, and `file-attributes` are available.

### `planck.shell`

This namespace imitates `clojure.shell`, and defining the `sh` function and `with-sh-dir` / `with-sh-env` macros that can be used to execute external command-line functions.

With this escape hatch, you can do nearly anything: Access remote webservers using `curl`, move files to remote hosts using `scp`, _etc._