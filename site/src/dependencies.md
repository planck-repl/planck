## Dependencies

<img width="100" align="right" style="margin: 0ex 1em" src="img/dependencies.jpg">
Source executed via Planck can depend on other bootstrapped-compatible libraries. To do so, the library must be on Planck's classpath, available either as source on an accessible filesystem, or bundled in a JAR.

> Planck can consume conventional JARs meant for use with ClojureScript obtained from [Clojars](https://clojars.org) or elsewhere.

Note that, since Planck employs _bootstrapped_ ClojureScript, not all regular ClojureScript libraries will work with Planck. In particular, libraries that employ macros that either rely on Java interop, or call macros in the same _compilation stage_ cannot work.  But libraries that employ straightforward macros that expand to ClojureScript work fine.

### Classpath Specification

Planck's classpath is specified by providing a colon-separated list of directories and/or JARs via the `-c` / `-​-​classpath` argument, or by the `PLANCK_CLASSPATH` environment variable. The default classpath, if none is specified, is taken as the current working directory.

For example,

```sh
planck -c src:/path/to/foo.jar:some-lib/src
```

will cause Planck to search in the `src` directory first, then in `foo.jar` next, and finally `some-lib/src` for files when processing `require`, `require-macros`, `import`, and `ns` forms.

### Abbreviated Dependency Specs

The `-D` / `-​-​dependencies` option can be used to specify coordinates for JARs installed in your local `.m2` repo: You can provide a comma separated list of `SYM:VERSION`, and paths to these JARs will be appended to your classpath.

For example,

```sh
planck -c src -D andare:0.7.0,org.clojure/test.check:0.10.0-alpha2
```

will expand to a classpath that specifies `src` followed by the paths to the Andare and `test.check` dependencies in your local `.m2` repository.

In order to use an explicitly-specified path to a Maven repository, you can additionally include `-L` or `-​-​local-repo`, specifying the repository path.

### Downloading Deps

While Planck can consume JARs from your local `.m2` repo, it doesn't take care of downloading them. An easy way to quickly download dependencies is to use `boot` with its `-d` option. For example, executing this will ensure the dependencies specified above are installed:

```
boot -d andare:0.7.0 -d org.clojure/test.check:0.10.0-alpha2
```

### Bundled Deps

Planck ships with many of the deps that are available to conventional ClojureScript code. In particular this includes [most of the Google Closure library](gcl.html) as well as these namespaces:

* `cljs.test`
* `clojure.core.reducers`
* `clojure.data`
* `clojure.template`
* `clojure.string`
* `clojure.set`
* `clojure.walk`
* `clojure.zip`

In addition, Planck ships with these libraries:

* [Fipp](https://github.com/brandonbloom/fipp) 0.6.8
* [transit-cljs](https://github.com/cognitect/transit-cljs) 0.8.243

Note that bundled dependencies, which includes the core ClojureScript compiler namespaces, are loaded in preference to dependencies specified via `-c` / `-​-​classpath`, `-D` / `-​-​dependencies`, or `PLANCK_CLASSPATH`.

A consequence of this (as well as the fact that nearly all of the code that ships with Planck is AOT-compiled), means that Planck works with a fixed version of ClojureScript. (It is not possible to update the ClojureScript version by providing a path to a newer version via `-c` / `-​-​classpath`, `-D` / `-​-​dependencies`, or `PLANCK_CLASSPATH`.)

### Foreign Libs

It is possible to use foreign libraries with Planck.

> “Foreign” libraries are implemented in a language that is not ClojureScript. (In other words, JavaScript!)

Planck will honor any `deps.cljs` files on the classpath (including those embedded in a JAR file). A `deps.cljs` file will have a [`:foreign-libs`](https://clojurescript.org/reference/compiler-options#foreign-libs) specification for foreign dependencies, essentially indicating the synthetic namespace, the JavaScript file that needs to be loaded, and an indication of any other dependencies that need to be loaded.

> While `deps.cljs` files are usually bundled in JAR files in order to convey upstream foreign lib dependencies, you can also put a `deps.cljs` file directly on Planck's classpath in order to specify `:foreign-libs`. (This is useful since Planck doesn't provide a command line argument mechanism for specifying foreign libs.) 

One easy way to make use of foreign libs packaged in this manner is via the excellent [CLJSJS](http://cljsjs.github.io) project. While many of the libraries packaged by CLJSJS cannot work with Planck because they either require a browser environment or Node, some utility libraries work just fine.

Here's an example: Let's say you want to use the [long.js](https://github.com/dcodeIO/long.js) library. The first thing you'll need to do is to obtain the CLJSJS JAR containing this library. The easiest way to do this is to execute `boot -d cljsjs/long:3.0.3-1` as described in the Downloading Deps section above.

If you launch Planck wtih `planck -D cljsjs/long:3.0.3-1`, you can `(require 'cljsjs.long)` to load the library and then proceed to use it using ClojureScript's JavaScript interop capabilities:

```clojure-repl
cljs.user=> (require 'cljsjs.long)
nil
cljs.user=> (str (js/Long. 0xFFFFFFFF 0x7FFFFFFF))
"9223372036854775807"
cljs.user=> (str js/Long.MAX_UNSIGNED_VALUE)
"18446744073709551615"
```
