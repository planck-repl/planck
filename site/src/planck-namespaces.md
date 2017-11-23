## Planck Namespaces

<img width="100" align="right" style="margin: 0ex 1em" src="img/planck-namespaces.jpg">
In order to make Planck more useful for doing actual work and interacting with your computer and the outside world, some native I/O facilities have been added to the JavaScriptCore instance running in Planck and these have been exposed via a few namespaces. To make things easier to use, the functions in these namespaces adhere fairly closely to existing Clojure / ClojureScript analogs.

The code for these namespaces is included directly _in the Planck binary_, so they are always available—you just need to `require` them.

These namespaces comprise
* `planck.core`
* `planck.repl`
* `planck.io`
* `planck.shell`
* `planck.http`

To explore these namespaces, you can evaluate `(dir planck.core)`, for example, to see the symbols in `planck.core`, and then use the `doc` macro to see the docs for any of the symbols.

Online documentation for the namespaces is available: [Planck SDK](sdk.html).

### planck.core

([Online docs](planck-core.html))

This namespace includes basic I/O capabilities like `slurp`, `spit` and `read-line`. The I/O facilities are expressed in protocols defined in `planck.core` modeled after those in Clojure, like `IReader`, `IOutputStream`, _etc._, and these capabilities cooperate with facilities defined in `planck.io`.

Planck core also hosts some dynamic vars of interest like `*in*` and `*out*`.

The `planck.core/file-seq` function imitates `clojure.core/file-seq`.

If you need to prompt for a password, take a look at `planck.core/read-password`.

The `planck.core` defines `eval` and other dynamic functions like `resolve`, `ns-resolve`, and `intern`.

Additionally, `planck.core/exit` is a function that takes an integer `exit-value` argument, so you can cause a Planck script to exit with any desired Unix exit value.

### planck.repl

([Online docs](planck-repl.html))

This namespace includes a few macros that are useful when working at the REPL, such as `doc`, `dir`, `source`, _etc_.

### planck.io

([Online docs](planck-io.html))

This namespace defines a lot of the `IOFactory` machinery, imitating `clojure.java.io`.

Additionally, filesystem facilities like `file`, `delete-file`, and `file-attributes` are available.

### planck.shell

([Online docs](planck-shell.html))

This namespace imitates `clojure.shell`, and defining the `sh` function and `with-sh-dir` / `with-sh-env` macros that can be used to execute external command-line functions.

With this escape hatch, you can do nearly anything: Move files to remote hosts using `scp`, _etc._

### planck.http

([Online docs](planck-http.html))

This namespace provides facilities for interacting with HTTP servers. For example:

```
(planck.http/get "http://planck-repl.org")
```

will fetch the main page of the Planck website, returning the status code, headers, and body in a map structure.