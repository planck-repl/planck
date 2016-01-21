# Planck

A command-line bootstrapped ClojureScript REPL for OS X based on JavaScriptCore.

Home page: [planck-repl.org](http://planck-repl.org)

# Installing

```
brew install planck
```

Binaries for manual download are [available](http://planck-repl.org/download.html).

# Building 

Planck's build employs scripts written in ClojureScript executed by Planck, so you will need to install a Planck binary either via Homebrew or manually before building.

## Release Build

1. `script/build`
2. Resulting binary is in `build/Release/planck`

## Tests

1. `script/test`

![Build Status](https://circleci.com/gh/mfikes/planck.png?circle-token=:circle-token)

## Development 

1. In the `plank-cljs` directory, do `script/build`
2. `open planck.xcodeproj`

### Bundling

For development, things are set up so that the on-disk ClojureScript compiler output is used (the `-o` or `--out` parameter). To instead have the output bundled into the binary, in `planck-cljs`, run `script/bundle` and then run Planck without the `--out` option. Bundling is done when doing a top-level `script/build` to produce a binary.

# License

Copyright © 2015–2016 Mike Fikes and Contributors

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
