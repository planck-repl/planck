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

Java, `clang`, `cmake`.

## Dependencies

  - macOS: `brew install pkg-config libzip icu4c`
  - Ubuntu 14.04: `sudo apt-get install javascriptcoregtk-3.0 libglib2.0-dev libzip-dev libcurl4-gnutls-dev libicu-dev`
  - Ubuntu 16.04: `sudo apt-get install javascriptcoregtk-4.0 libglib2.0-dev libzip-dev libcurl4-gnutls-dev libicu-dev`
  - Arch: `pacman -S webkitgtk libzip zlib`
  - Debian GNU Linux 8: `sudo apt-get install pkg-config javascriptcoregtk-4.0 libglib2.0-dev libzip-dev libcurl4-gnutls-dev libicu-dev`

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

Copyright © 2015–2016 Mike Fikes and Contributors

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
