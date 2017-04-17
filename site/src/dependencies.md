## Dependencies

<img width="100" align="right" style="margin: 0ex 1em" src="img/dependencies.jpg">
Source executed via Planck can depend on other bootstrapped-compatible libraries. To do so, the library must be available either as source on an accessible filesystem, or bundled in a JAR, and included in Planck's classapth.

> Planck can consume conventional JARs meant for use with ClojureScript obtained from [Clojars](https://clojars.org) or elsewhere.

Planck's classpath is specified by providing a colon-separated list of directories and/or JARs via the `-c` or `-​-​classpath` argument, or by the `PLANCK_CLASSPATH` environment variable. The default classpath, if none is specified, is taken as the current working directory.

For example,

```sh
planck -c src:/path/to/foo.jar:some-lib/src
```

will cause Planck to search in the `src` directory first, then in `foo.jar` next, and finally `some-lib/src` for files when processing `require`, `require-macros`, `import`, and `ns` forms.

> Paths to JARs cached locally via Leiningen, Boot, or Maven (usually under `/Users/<username>/.m2/repository`) can be used. See the section below on Leiningen and Boot for tips on dependency management.

Note that, since Planck employs bootstrapped ClojureScript, not all regular ClojureScript libraries will work with Planck. In particular, libraries that employ macros that either rely on Java interop, or call macros in the same _compilation stage_ cannot work.  But libraries that employ straightforward macros that expand to ClojureScript work fine.

> One example of Planck using a dependency: This documentation is written in markdown, but converted to HTML using Planck using Dmitri Sotnikov's  [markdown-clj](https://github.com/yogthos/markdown-clj) library. This library is written with support for regular ClojureScript, but it also works perfectly well in bootstrapped ClojureScript.

### Shipping Deps

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

* [Fipp](https://github.com/brandonbloom/fipp) 0.6.7
* [transit-cljs](https://github.com/cognitect/transit-cljs) 0.8.239

Note that bundled dependencies, which includes the core ClojureScript compiler namespaces, are loaded in preference to dependencies specified via `-c`, `-​-​classpath`, or `PLANCK_CLASSPATH`.

A consequence of this (as well as the fact that nearly all of the code that ships with Planck is AOT-compiled), means that Planck works with a fixed version of ClojureScript. (It is not possible to update the ClojureScript version by providing a path to a newer version via `-c`, `-​-​classpath`, or `PLANCK_CLASSPATH`.)

### Using Leiningen or Boot for JAR Dependency Management

Planck requires that JARs be available locally and on the classpath, but it doesn't take care of downloading JARs. One solution to this is to use either [Leiningen](http://leiningen.org) or [Boot](http://boot-clj.com) to manage dependencies for you, and to have those tools emit a classpath for use with Planck.

Here is an example using Leiningen: Let's say you want to use [clojurescript.csv](https://github.com/testdouble/clojurescript.csv) from Planck. First make a simple Leiningen `project.clj` just for the purpose of loading this dependency:

```clj
(defproject foo "0.1.0-SNAPSHOT"
  :dependencies [[testdouble/clojurescript.csv "0.2.0"]])
```

Now, with this in place, you can launch Planck using `lein classpath` to automatically generate the classpath string (and also automatically download any deps that haven't yet been downloaded). Here is a sample session showing this working, along with using the library within Planck.

```
$ planck -c`lein classpath`
Retrieving testdouble/clojurescript.csv/0.2.0/clojurescript.csv-0.2.0.pom from clojars
Retrieving testdouble/clojurescript.csv/0.2.0/clojurescript.csv-0.2.0.jar from clojars
cljs.user=> (require '[testdouble.cljs.csv :as csv])
nil
cljs.user=> (csv/write-csv [[1 2 3] [4 5 6]])
"1,2,3\n4,5,6"
```

If you are using Boot, the equivalent would be

```
$ planck -c`boot show -c`
```

#### Caching Classpath

Both Leiningen and Boot take a bit of time to verify that dependency artifacts have been downloaded. To make launching instant, just make a start shell script that looks like the following. (With this approach, be sure to manually delete the `.classpath` file if you change your dependencies.)

```
if [ ! -f .classpath ]; then
  classpath=`lein classpath | tee .classpath`
else
  classpath=`cat .classpath`
fi

planck -c $classpath
```

(And if you are using Boot, replace `lein classpath` with `boot show -c`.)

### Foreign Libs

It is possible to use foreign libraries with Planck.

> “Foreign” libraries are implemented in a language that is not ClojureScript. (In other words, JavaScript!)

Planck will honor a `deps.cljs` file embedded in a JAR file. A `deps.cljs` file will have a [`:foreign-libs`](https://github.com/clojure/clojurescript/wiki/Compiler-Options#foreign-libs) specification for upstream foreign dependencies packaged in the JAR, essentially indicating the synthetic namespace, the JavaScript file that needs to be loaded, and an indication of any other dependencies that need to be loaded. 

One easy way to make use of foreign libs packaged in this manner is via the excellent [CLJSJS](http://cljsjs.github.io) project. While many of the libraries packaged by CLJSJS cannot work with Planck because they either require a browser environment or Node, some utility libraries work just fine.

Here's an example: Let's say you want to use the [long.js](https://github.com/dcodeIO/long.js) library. The first thing you'll need to do is to obtain the CLJSJS JAR containing this library. The easiest way to do this is to place the CLJSJS `[cljsjs/long "3.0.3-1"]` dependency vector in a `project.clj` file as described in the previous section on using Leiningen for JAR deps.

If you pass the Leiningen-generated classpath containing this JAR to Planck, after start up you can `(require 'cljsjs.long)` to load the library and then proceed to use it using ClojureScript's JavaScript interop capabilities:

```clojure-repl
cljs.user=> (require 'cljsjs.long)
nil
cljs.user=> (str (js/Long. 0xFFFFFFFF 0x7FFFFFFF))
"9223372036854775807"
cljs.user=> (str js/Long.MAX_UNSIGNED_VALUE)
"18446744073709551615"
```
