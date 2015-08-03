# Planck

A command line bootstrapped ClojureScript REPL for OS X based on JavaScriptCore.

# Downloading

Head over to [planck.fikesfarm.com](http://planck.fikesfarm.com).

# Building 

## Release Build

1. `./build.sh`
2. Resulting binary is in `build/Release/planck`

## Development 

1. In the `plank-cljs` directory, do `script/build`
2. Do a `pod install` in the top level.
3. `open planck.xcworkspace`

### Bundling

For development, things are setup so that the on-disk ClojureScript compiler output is used (the `-o` or `--out` parameter). To instead have the output bundled into the binary, run `script/bundle` and then run Planck without the `--out` option.

### Ambly

In order to have Planck launch an Ambly server (so that you can connect to it with an Ambly REPL for development), pass the `--ambly-server` option to Planck.

# License

Distributed under the Eclipse Public License, which is also used by ClojureScript.
