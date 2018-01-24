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

Specify `-fast` to quickly build a development version that skips Closure optimization:

```
$ script/build -fast
```

If you specify `-Sdeps`, it will be passed through to the underlying [`clojure`](https://clojure.org/guides/deps_and_cli) command during the build process. This can be used, for example, to specify a ClojureScript dep to use:

```
$ script/build -Sdeps "{:deps {org.clojure/clojurescript {:mvn/version \"1.9.946\"}}}"
```

## Tests

```
$ script/test
```


# License

Copyright © 2015–2018 Mike Fikes and Contributors

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
