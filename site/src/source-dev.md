## Source Dev

<img width="100" align="right" style="margin: 0ex 1em" src="img/source-dev.jpg">
When launching Planck using `plk`, you can specify a vector of source directories in `deps.edn`:

```
{:paths ["src" "test"]}
```

With this, you can put this code in `src/foo/core.cljs`:

```clojure
(ns foo.core)

(defn square
  [x]
  (* x x))
```

Then, if you launch Planck via `plk`

```
$ plk
```

you can then load the code in `foo.core` by doing

```
cljs.user=> (require 'foo.core)
```

If you subsequently edit `src/foo/core.cljs`, say, to define a new function, or to change existing functions, you can reload that code by adding the `:reload` flag:

```
cljs.user=> (require 'foo.core :reload)
```

Alternatively, when launching Planck you can use the `-c` or `-​-​classpath` option, or the `PLANCK_CLASSPATH` environment variable, to specify a colon-delimited list of source directories and JARs to search in when loading code using `require` and `require-macros`. You can also use `-D` or `-​-​dependencies` provide a comma separated list of `SYM:VERSION`, indicating libraries to be loaded from the local Maven repository. (See the Dependencies section of this guide.)

Using `-c`, you can specify `"src"` and `"test"` as source directories via

```
planck -c src:test
```

### Macros

If you define macros in bootstrap ClojureScript (which is the mode that Planck runs in), the macros must be written in ClojureScript (as opposed to Clojure, as is done with regular ClojureScript).

Even though the macros are defined in ClojureScript, they are defined in `*.clj` files. You can, if you wish, also define macros in `*.cljc` files, but when they are processed, the `:cljs` branch of reader conditionals will be used.

When writing macros for self-hosted ClojureScript, they must abide the same rules that apply to all ClojureScript code. In particular, this means a macro cannot call another macro defined in the same _compilation stage_: If a macro calls another macro _during_ expansion, then one approach is to define the called macro in a “higher” namespace (possibly arranged in a tower). On the other hand, if a macro simply _expands_ to a call to another macro defined in the same namespace, then the compilation staging rules are satisfied.

### Source Mapping

If an exception is thrown, you may see a stack trace. (If not, you can use `pst` to print the stack trace for an exception.) When trace lines correspond to code that originated from files, the line numbers are mapped from the executed JavaScript back to the original ClojureScript. 

### Tagged Literals

Planck supports tagged literals. For an overview of this feature see the [Tagged Literals](https://clojure.org/reference/reader#tagged_literals) documentation.

Planck searches for `data_readers.cljc` files at the root of the classpath, and the values of the data reader maps are associated with vars that must defined in ClojureScript.

For example, lets say a `data_readers.cljc` file contains:

```
{foo/bar my.project.foo/bar}
```

Then, in order to parse `#foo/bar [1 2 3]`, `#'my.project.foo/bar` must be defined in ClojureScript. (This differs from JVM ClojureScript, where this must be defined in Clojure.) This could be accomplished by defining a namespace like the following that is loaded into Planck before expressions involving `#foo/bar` are read.

```
(ns my.project.foo)

(defn bar [x] ,,,)
```

Note that, in either case (JVM ClojureScript, or self-hosted ClojureScript), the reader function `bar` above must return code that is compilable in ClojureScript.

### Bootstrap ClojureScript

It is possible to make use of the `cljs.js` namespace within Planck. But, since Planck is built with the `:dump-core` ClojureScript compiler option set to `false`, calls to the 0-arity version of `cljs.js/empty-state` will produce a state atom which lacks `cljs.core` analysis metadata. To produce a populated compiler state atom, you can make use of `planck.core/init-empty-state`:

```
(require 'cljs.js 'planck.core)

(def st (cljs.js/empty-state planck.core/init-empty-state))

(cljs.js/eval-str st "(map inc [1 2 3])" nil
  {:eval cljs.js/js-eval :context :expr} identity)
```
