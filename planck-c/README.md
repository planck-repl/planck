# planck-c

This is an in-progress port of Planck to C, supporting multiple
platforms.

**Warning:** *This is very much a work in progress.  Lots of stuff is still missing, things might not work, ...  Feel free to port things from the Objective-C version of planck.*

## Development

- prerequisites: Java, maven, lein
- install `javascriptcore`, `libzip`, `zlib`
    - on mac: `brew install pkg-config libzip`
    - on arch: `pacman -S clang webkitgtk libzip zlib`
    - on Ubuntu 14.04: `sudo apt-get install clang javascriptcoregtk-3.0 libglib2.0-dev libzip-dev libcurl4-gnutls-dev`
- `make bundle-and-build`
- have fun: `./planck`

[![Build Status](https://travis-ci.org/mfikes/planck.svg?branch=master)](https://travis-ci.org/mfikes/planck)

## Implementation

- based on JavaScriptCore (from webkitgtk)
- uses `planck-cljs`
- access to native capabilities via a reimplementation of
    planck's Objective-C part

A lot of stuff is still missing (in fact most of it).
