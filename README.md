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

### Parameterized Builds

Set the optional `FAST_BUILD` environment variable to quickly build a development version that skips Closure optimization:

```
$ FAST_BUILD=1 script/build
```

To build against a specific (locally installed) ClojureScript version, specify `CLJS_VERSION`:

```
$ CLJS_VERSION=1.9.908 script/build
```

To locally build and use a specific ClojureScript commit, specify `CLJS_COMMIT`:

```
$ CLJS_COMMIT=d450122 script/build
```

## Tests

```
$ script/test
```


# License

Copyright © 2015–2018 Mike Fikes and Contributors

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
