# Planck

A stand-alone ClojureScript REPL for OS X based on JavaScriptCore.

Home page: [planck-repl.org](http://planck-repl.org)

# Installing / Updating

```
$ brew install planck
```

or [download it](http://planck-repl.org/download.html).

If you already have Planck installed via Homebrew and you'd like to update to the latest version:

```
$ brew update
$ brew upgrade planck
```

# Using

Launch Planck by entering `planck` at the terminal.

Get help on command-line options by issuing `planck -h`.

For more details, see the [Planck User Guide](http://planck-repl.org/guide.html).

# Building 

1. `script/build`
2. Resulting binary is in `build/Release/planck`

You can run tests by doing:

1. `script/test`

[![Circle CI](https://circleci.com/gh/mfikes/planck.svg?style=svg)](https://circleci.com/gh/mfikes/planck)

Additional details on developing Planck are [available](https://github.com/mfikes/planck/wiki/Development).

# License

Copyright © 2015–2016 Mike Fikes and Contributors

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
