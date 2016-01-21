## Setup

<img width="100" align="right" style="margin: 0ex 1em" src="img/setup.jpg">
Planck runs on OS X. 

Planck requires no external dependencies. (There is no dependency on either the Java JVM or Node.js.)

### Homebrew

The easiest way to install Planck is via [Homebrew](http://brew.sh):

```sh
$ brew install planck
```

Homebrew installation is currently supported for OS X 10.9 Mavericks through OS X 10.11 El Capitan.

If you'd like to install the latest unreleased version of Planck (directly from master in the GitHub repository), you can do the following

```sh
$ brew install --HEAD planck
```

> To do this requires that you have the tools installed which are needed to build Planck. This includes `Java`, `lein` and `Xcode`.

### Download

You can download Planck. It ships as a single-file binary so it is as easy as putting it in your path then making it executable:

```
$ chmod +x planck
```

You can download Planck (including older releases) at [http://planck-repl.org/download.html](download.html).

Planck 1.9 supports OS X versions 10.7 Lion through 10.11 El Capitan.

### Bug Reporting

If you happen to encounter any issues with Planck, issues are tracked on GitHub at [https://github.com/mfikes/planck/issues](https://github.com/mfikes/planck/issues).
