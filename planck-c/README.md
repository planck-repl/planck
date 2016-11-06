# planck-c

Planck 2.0: This is an in-progress port of Planck 1.x to C, supporting Linux and OS X.

## Development

- prerequisites: Java, `clang`, `cmake`
- install deps
    - on macOS: `brew install pkg-config libzip icu4c`
    - on Arch: `pacman -S webkitgtk libzip zlib`
    - on Ubuntu 14.04: `sudo apt-get install javascriptcoregtk-3.0 libglib2.0-dev libzip-dev libcurl4-gnutls-dev libicu-dev`
    - on Ubuntu 16.04: `sudo apt-get install javascriptcoregtk-4.0 libglib2.0-dev libzip-dev libcurl4-gnutls-dev libicu-dev`
- `script/build-c` Note: if the build hangs, see [workaround in #329](https://github.com/mfikes/planck/issues/329#issuecomment-257116266)
- you can run the unit and integration tests with `script/test-all-c`
- have fun: `./planck-c/build/planck`

[![Build Status](https://travis-ci.org/mfikes/planck.svg?branch=master)](https://travis-ci.org/mfikes/planck)
