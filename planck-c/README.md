# planck-c

Planck 2.0: This is an in-progress port of Planck 1.x to C, supporting Linux and OS X.

**Warning:** *This is very much a work in progress.  Lots of stuff is still missing, things might not work.  Feel free to port things from the Objective-C version of Planck 1.x. See [TODOs](https://github.com/mfikes/planck/wiki/Planck-C-TODOs).*

## Development

- prerequisites: Java, `clang`
- install deps
    - on mac: `brew install pkg-config libzip icu4c`
    - on arch: `pacman -S webkitgtk libzip zlib`
    - on Ubuntu 14.04: `sudo apt-get install javascriptcoregtk-3.0 libglib2.0-dev libzip-dev libcurl4-gnutls-dev libicu-dev`
    - on Ubuntu 16.04: `sudo apt-get install javascriptcoregtk-4.0 libglib2.0-dev libzip-dev libcurl4-gnutls-dev libicu-dev`
- `make bundle-and-build`
- have fun: `./planck`

[![Build Status](https://travis-ci.org/mfikes/planck.svg?branch=master)](https://travis-ci.org/mfikes/planck)

You can run the "integration tests" by executing the following from the root of the repo tree: `int-test/script/run-tests-c`. (Those don't all pass yet.)

## Implementation

- based on JavaScriptCore (from webkitgtk)
- uses `planck-cljs`
- access to native capabilities via a reimplementation of
    Planck's Objective-C part

A lot of stuff is still missing (in fact most of it).
