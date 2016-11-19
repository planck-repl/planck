# Planck

A stand-alone ClojureScript REPL for macOS based on JavaScriptCore.

Home page: [planck-repl.org](http://planck-repl.org)

> If you are running Linux, Windows, or macOS, also be sure to check out [Lumo](https://github.com/anmonteiro/lumo), a stand-alone ClojureScript REPL based on Node.js and V8 that is capable of using NPM libraries.

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

1. `script/build-objc`
2. Resulting binary is in `build/Release/planck`

You can run tests by doing:

1. `script/test-objc`

Additional details on developing Planck are [available](https://github.com/mfikes/planck/wiki/Development).

# Planck 2.0.0 Beta

Planck 2.0.0 beta Ubuntu and macOS binaries are available [for download](http://planck-repl.org/download-beta.html).

Instructions on building the Planck 2.0.0 beta on Linux and macOS are in the [planck-c](https://github.com/mfikes/planck/tree/master/planck-c) tree.

# License

Copyright © 2015–2016 Mike Fikes and Contributors

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
