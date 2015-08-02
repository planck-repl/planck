# Planck

A command line bootstrapped ClojureScript REPL for OS X based on JavaScriptCore.

# Downloading

Head over to [planck.fikesfarm.com](http://planck.fikesfarm.com).

# Building 

1. Clone and build [ClojureScript master](https://github.com/clojure/clojurescript) (script/build).
2. Set the `project.clj` file so that it matches the ClojureScript master build number.
3. In the `plank-cljs` directory, do `script/build`
4. Do a `pod install` in the top level.
5. `open planck.xcworkspace`
6. Edit the scheme to pass in src, out, and repl arguments as below and then run it via Xcode

```
-s $PROJECT_DIR/planck-cljs/src
-o $PROJECT_DIR/planck-cljs/out
-r
```

In order to work on it using Ambly, set `runAmblyReplServer` to `YES` in the `Planck` class and then `script/repl`.

In order to bundle things up for standalone execution, `script/bundle` and then build it with `useBundledOutput` set to `YES` in the `Planck` class.

# License

Distributed under the Eclipse Public License, which is also used by ClojureScript.
