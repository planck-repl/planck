# planck-c

Planck 2.0: This is an in-progress port of Planck 1.x to C, supporting Linux and macOS.

- Prerequisites: 
  - Java
  - `clang`
  - `cmake`
- Dependencies:
  - macOS: `brew install pkg-config libzip icu4c`
  - Arch: `pacman -S webkitgtk libzip zlib`
  - Ubuntu 14.04: `sudo apt-get install javascriptcoregtk-3.0 libglib2.0-dev libzip-dev libcurl4-gnutls-dev libicu-dev`
  - Ubuntu 16.04: `sudo apt-get install javascriptcoregtk-4.0 libglib2.0-dev libzip-dev libcurl4-gnutls-dev libicu-dev`
  - Debian GNU Linux 8: `sudo apt-get install pkg-config javascriptcoregtk-4.0 libglib2.0-dev libzip-dev libcurl4-gnutls-dev libicu-dev`
- Compile
  - `script/build`
- Unit and integration tests
  - `script/test-all`
- Run
  - `planck-c/build/planck`

[![Build Status](https://travis-ci.org/mfikes/planck.svg?branch=master)](https://travis-ci.org/mfikes/planck)
