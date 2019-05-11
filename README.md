# Planck

A stand-alone ClojureScript REPL for macOS and Linux based on JavaScriptCore.

Home page: [planck-repl.org](https://planck-repl.org)

# Installing

On macOS:
```
$ brew install planck
```

On Ubuntu:
```
sudo add-apt-repository ppa:mfikes/planck
sudo apt-get update
sudo apt-get install planck
```

For other Linux distros, [download](https://planck-repl.org/binaries/) a binary or see [Building](https://github.com/planck-repl/planck#building) below.

# Using

Launch Planck by entering `planck` or `plk` at the terminal. 

> The `plk` script executes `planck`, while integrating with the [`clojure`](https://clojure.org/guides/getting_started) CLI tool to add support for `deps.edn` and classpath-affecting options such as `-Aalias`.

Get help on command-line options by issuing `planck -h` or `plk -h`.

For more details, see the [Planck User Guide](https://planck-repl.org/guide.html).

### Ported Clojure Functionality

It is possible to write Clojure-idiomatic scripts like the following:

```clojure
(require '[planck.core :refer [line-seq with-open]]
         '[planck.io :as io]
         '[planck.shell :as shell])

(with-open [rdr (io/reader "input.txt")]
  (doseq [line (line-seq rdr)]
    (println (count line))))

(shell/sh "say" "done")
```    

Many of the familiar functions and macros unique to Clojure have been ported:

#### clojure.core/ -> planck.core/ 

[eval](https://planck-repl.org/planck-core.html#eval), 
[file-seq](https://planck-repl.org/planck-core.html#file-seq),
[find-var](https://planck-repl.org/planck-core.html#find-var),
[load-reader](https://planck-repl.org/planck-core.html#load-reader),
[load-string](https://planck-repl.org/planck-core.html#load-string),
[line-seq](https://planck-repl.org/planck-core.html#line-seq),
[intern](https://planck-repl.org/planck-core.html#intern),
[ns-aliases](https://planck-repl.org/planck-core.html#ns-aliases),
[ns-refers](https://planck-repl.org/planck-core.html#ns-refers),
[ns-resolve](https://planck-repl.org/planck-core.html#ns-resolve),
[read](https://planck-repl.org/planck-core.html#read),
[read-line](https://planck-repl.org/planck-core.html#read-line),
[read-string](https://planck-repl.org/planck-core.html#read-string),
[resolve](https://planck-repl.org/planck-core.html#resolve),
[slurp](https://planck-repl.org/planck-core.html#slurp),
[spit](https://planck-repl.org/planck-core.html#spit),
[with-in-str](https://planck-repl.org/planck-core.html#with-in-str),
[with-open](https://planck-repl.org/planck-core.html#with-open)

#### clojure.java.io/ -> planck.io/

[as-file](https://planck-repl.org/planck-io.html#as-file),
[as-relative-path](https://planck-repl.org/planck-io.html#as-relative-path),
[as-url](https://planck-repl.org/planck-io.html#as-url),
[delete-file](https://planck-repl.org/planck-io.html#delete-file),
[file](https://planck-repl.org/planck-io.html#file),
[input-stream](https://planck-repl.org/planck-io.html#input-stream),
[make-input-stream](https://planck-repl.org/planck-io.html#make-input-stream),
[make-output-stream](https://planck-repl.org/planck-io.html#make-output-stream),
[make-parents](https://planck-repl.org/planck-io.html#make-parents),
[make-reader](https://planck-repl.org/planck-io.html#make-reader),
[make-writer](https://planck-repl.org/planck-io.html#make-writer),
[output-stream](https://planck-repl.org/planck-io.html#output-stream),
[reader](https://planck-repl.org/planck-io.html#reader),
[resource](https://planck-repl.org/planck-io.html#resource),
[writer](https://planck-repl.org/planck-io.html#writer)

#### clojure.java.shell/ -> planck.shell/

[sh](https://planck-repl.org/planck-shell.html#sh),
[with-sh-dir](https://planck-repl.org/planck-shell.html#with-sh-dir),
[with-sh-env](https://planck-repl.org/planck-shell.html#with-sh-env)

# Building 

If using macOS or Ubuntu, you can install pre-built binaries as described above under "Installing". The instructions here can be used to build, test, and optionally install Planck on your machine.

[![Build Status](https://travis-ci.org/planck-repl/planck.svg?branch=master)](https://travis-ci.org/planck-repl/planck)

## Prerequisites 

See [Building Wiki](https://github.com/planck-repl/planck/wiki/Building) for setting up OS-specific build tooling and dependencies.

Pre-made build environments for various environments are available in [build-envs](https://github.com/planck-repl/build-envs).

## Compiling

```
$ script/build
```

The resulting binary will be `planck-c/build/planck`.

Specify `--fast` to quickly build a development version that skips Closure optimization:

```
$ script/build --fast
```

If you specify `-Sdeps` or `-R<alias>`, it will be passed through to the underlying [`clojure`](https://clojure.org/guides/deps_and_cli) command during the build process. This can be used to specify a ClojureScript dep to use.

## Tests

```
$ script/test
```

## Installing

The following will install Planck under the prefix `/usr/local`:

```
$ sudo script/install
```

If you'd like to install Planck under a different prefix, you may pass `-p`. For example:

```
$ sudo script/install -p /usr
```

# License

Copyright © 2015–2019 Mike Fikes and Contributors

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
