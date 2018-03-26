# Planck

A stand-alone ClojureScript REPL for macOS and Linux based on JavaScriptCore.

Home page: [planck-repl.org](http://planck-repl.org)

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

For other Linux distros, see Building below.

# Using

Launch Planck by entering `planck` at the terminal.

Get help on command-line options by issuing `planck -h`.

For more details, see the [Planck User Guide](http://planck-repl.org/guide.html).

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

[eval](http://planck-repl.org/planck-core.html#eval), 
[file-seq](http://planck-repl.org/planck-core.html#file-seq),
[line-seq](http://planck-repl.org/planck-core.html#line-seq),
[intern](http://planck-repl.org/planck-core.html#intern),
[ns-resolve](http://planck-repl.org/planck-core.html#ns-resolve),
[read](http://planck-repl.org/planck-core.html#read),
[read-line](http://planck-repl.org/planck-core.html#read-line),
[read-string](http://planck-repl.org/planck-core.html#read-string),
[resolve](http://planck-repl.org/planck-core.html#resolve),
[slurp](http://planck-repl.org/planck-core.html#slurp),
[spit](http://planck-repl.org/planck-core.html#spit),
[with-open](http://planck-repl.org/planck-core.html#with-open)

#### clojure.java.io/ -> planck.io/

[as-file](http://planck-repl.org/planck-io.html#as-file),
[as-url](http://planck-repl.org/planck-io.html#as-url),
[delete-file](http://planck-repl.org/planck-io.html#delete-file),
[file](http://planck-repl.org/planck-io.html#file),
[input-stream](http://planck-repl.org/planck-io.html#input-stream),
[make-input-stream](http://planck-repl.org/planck-io.html#make-input-stream),
[make-output-stream](http://planck-repl.org/planck-io.html#make-output-stream),
[make-reader](http://planck-repl.org/planck-io.html#make-reader),
[make-writer](http://planck-repl.org/planck-io.html#make-writer),
[output-stream](http://planck-repl.org/planck-io.html#output-stream),
[reader](http://planck-repl.org/planck-io.html#reader),
resource,
[writer](http://planck-repl.org/planck-io.html#writer)

#### clojure.java.shell/ -> planck.shell/

[sh](http://planck-repl.org/planck-shell.html#sh),
[with-sh-dir](http://planck-repl.org/planck-shell.html#with-sh-dir),
[with-sh-env](http://planck-repl.org/planck-shell.html#with-sh-env)

# Building 

[![Build Status](https://travis-ci.org/mfikes/planck.svg?branch=master)](https://travis-ci.org/mfikes/planck)

## Prerequisites 

See [Building Wiki](https://github.com/mfikes/planck/wiki/Building) for setting up OS-specific build tooling and dependencies.

Pre-made build environments for various environments are available in [build-envs](https://github.com/mfikes/planck/tree/master/build-envs).

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


# License

Copyright © 2015–2018 Mike Fikes and Contributors

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
