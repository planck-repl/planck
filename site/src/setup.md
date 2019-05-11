## Setup

<img width="100" align="right" style="margin: 0ex 1em" src="img/setup.jpg">
Planck runs on macOS and Linux. 

Planck requires no external dependencies. (There is no need for either the Java JVM or Node.js.)

### Homebrew

#### Install

The easiest way to install Planck on macOS is via [Homebrew](https://brew.sh):

```sh
brew install planck
```

On Ubuntu you can use `apt-get`:

```sh
sudo add-apt-repository ppa:mfikes/planck
sudo apt-get update
sudo apt-get install planck
```

For other Linux distros, [binary downloads](https://planck-repl.org/binaries/) are available for some distros. Otherwise, see Building below.

#### Install Master

If you'd like to use Homebrew to install the latest unreleased version of Planck (directly from master in the GitHub repository), you can do the following:

```sh
brew remove planck
brew install --HEAD planck
```

#### Building

To build Planck on Linux or macOS, get a copy of the source tree, install the [needed dependencies](https://github.com/planck-repl/planck/wiki/Building) and run

```sh
script/build
```

This results in a binary being placed in `planck-c/build`.

You can then optionally install Planck, the `plk` script, and associated man pages via

```sh
script/install
```

### Bug Reporting

If you happen to encounter any issues with Planck, issues are tracked on GitHub at [https://github.com/planck-repl/planck/issues](https://github.com/planck-repl/planck/issues).
