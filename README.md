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

[![cljdoc badge](https://cljdoc.org/badge/planck/planck)](https://cljdoc.org/d/planck/planck/CURRENT)

Launch Planck by entering `planck` or `plk` at the terminal. 

> The `plk` script executes `planck`, while integrating with the [`clojure`](https://clojure.org/guides/getting_started) CLI tool to add support for `deps.edn` and classpath-affecting options such as `-Aalias`.

Get help on command-line options by issuing `planck -h` or `plk -h`.

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

[file-seq](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.core#file-seq),
[find-var](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.core#find-var),
[load-reader](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.core#load-reader),
[load-string](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.core#load-string),
[line-seq](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.core#line-seq),
[intern](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.core#intern),
[ns-aliases](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.core#ns-aliases),
[ns-refers](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.core#ns-refers),
[ns-resolve](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.core#ns-resolve),
[read](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.core#read),
[read-line](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.core#read-line),
[read-string](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.core#read-string),
[resolve](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.core#resolve),
[slurp](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.core#slurp),
[spit](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.core#spit),
[with-in-str](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.core#with-in-str),
[with-open](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.core#with-open)

#### clojure.java.io/ -> planck.io/

[as-file](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.io#as-file),
[as-relative-path](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.io#as-relative-path),
[as-url](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.io#as-url),
[delete-file](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.io#delete-file),
[file](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.io#file),
[input-stream](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.io#input-stream),
[make-input-stream](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.io#make-input-stream),
[make-output-stream](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.io#make-output-stream),
[make-parents](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.io#make-parents),
[make-reader](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.io#make-reader),
[make-writer](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.io#make-writer),
[output-stream](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.io#output-stream),
[reader](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.io#reader),
[resource](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.io#resource),
[writer](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.io#writer)

#### clojure.java.shell/ -> planck.shell/

[sh](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.shell#sh),
[with-sh-dir](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.shell#with-sh-dir),
[with-sh-env](https://cljdoc.org/d/planck/planck/CURRENT/api/planck.shell#with-sh-env)

# Building 

If using macOS or Ubuntu, you can install pre-built binaries as described above under "Installing". The instructions here can be used to build, test, and optionally install Planck on your machine.

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

[![Build Status](https://travis-ci.org/planck-repl/planck.svg?branch=master)](https://travis-ci.org/planck-repl/planck) Travis (macOS & Linux on x86)

[![Build Status](https://cloud.drone.io/api/badges/planck-repl/planck/status.svg?branch=master)](https://cloud.drone.io/planck-repl/planck) Drone (Linux on ARM) 

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
