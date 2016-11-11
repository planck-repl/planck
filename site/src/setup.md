## Setup

<img width="100" align="right" style="margin: 0ex 1em" src="img/setup.jpg">
Planck runs on macOS. 

Planck requires no external dependencies. (There is no need for either the Java JVM or Node.js.)

### Homebrew

#### Install

The easiest way to install Planck is via [Homebrew](http://brew.sh):

```sh
$ brew install planck
```

#### Update

If you already have Planck installed via Homebrew and you'd like to update to the latest version:

```
$ brew update
$ brew upgrade planck
```

#### Install Master

If you'd like to use Homebrew to install the latest unreleased version of Planck (directly from master in the GitHub repository), you can do the following:

```sh
$ brew remove planck
$ brew install --HEAD planck
```

### Manual Download and Install

You can download Planck. It ships as a single-file binary, so it is as easy as putting it in your path then making it executable:

```
$ chmod +x planck
```

You can download Planck binaries (including older releases) at [http://planck-repl.org/download.html](download.html).

### Bug Reporting

If you happen to encounter any issues with Planck, issues are tracked on GitHub at [https://github.com/mfikes/planck/issues](https://github.com/mfikes/planck/issues).
