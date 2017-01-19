# Planck

A stand-alone ClojureScript REPL for macOS based on JavaScriptCore.

Home page: [planck-repl.org](http://planck-repl.org)

# Installing

```
$ brew install planck
```

or [download it](http://planck-repl.org/download.html).

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

## Tests

```
$ script/test
```


# License

Copyright © 2015–2017 Mike Fikes and Contributors

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
