## Dependencies

<img width="100" align="right" style="margin: 0ex 1em" src="img/dependencies.jpg">
Planck can depend on other libraries. To do so, the library must be available on an accessible filesystem, either as a source tree or bundled in a JAR, and included in Planck's classpath. You specify the classpath for Planck by providing a colon-separated list of directories and/or JARs via the `-c` or `-​-​classpath` argument.

For example,

```sh
planck -c src:/path/to/foo.jar:some-lib/src
```

will cause Planck to search in the `src` directory first, and then in `foo.jar` next, and finally `some-lib/src` for files when processing `require`, `require-macros`, and `import` direcives (either in the REPL, or as part of `ns` forms.)

> Paths to JARs cached locally via Maven (usually under `/Users/<username>/.m2/repository`) will work fine. See the section below on Leiningen for tips on dependency management.

Note that, since Planck employs bootstrapped ClojureScript, not all regular ClojureScript libraries may work with Planck. In particular, libraries that employ macros that rely on Java interop cannot work. But libraries that employ straightworward macros that expand to ClojureScript work fine.

> One example of Planck using a dependency: This documentation is written in markdown, but converted to HTML _using Planck itself_ using Dmitri Sotnikov's  [markdown-clj](https://github.com/yogthos/markdown-clj) library. This library is written with support for regular ClojureScript, but it also works perfectly well in bootstrapped ClojureScript.

### Using Leiningen for JAR Dependency Management

Planck requires that JARs be available locally and on the classpath, but it doen't take care of downloading JARs. One solution to this is to use [Leiningen](http://leiningen.org) to manage dependencies for you, and to use its `classpath` option to generate a classpath string for use with Planck.

Here is an example. Let's say you want to use [clojurescript.csv](https://github.com/testdouble/clojurescript.csv) from Planck. First make a simple Leiningen `project.clj` just for the purpose of loading this dependency:

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
