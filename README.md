# Planck

A command line bootstrapped ClojureScript REPL for OS X based on JavaScriptCore.

Read the [blog post](http://blog.fikesfarm.com/posts/2015-07-16-fast-javascriptcore-desktop-clojurescript-repl.html).

# Download / Running

You can download the binary file:

[planck.gz](http://blog.fikesfarm.com/planck.gz) size 1169543 bytes, md5 `eacdc5711a941450ae5823aaba221b9b`

# Building 

1. Clone and build [ClojureScript master](https://github.com/clojure/clojurescript) (script/build).
2. Set the project.clj file so that it matches the ClojureScript master build number.
3. In the ClojureScript/planck directory, do `script/build`
4. Do a pod install in the top level.
5. `open planck.xcworkspace`
6. Edit the scheme to pass in src and out arguments as below and then run it via Xcode

```
-s $PROJECT_DIR/ClojureScript/planck/src
-o $PROJECT_DIR/ClojureScript/planck/out
```

In order to work on it using Ambly, set `runAmblyReplServer` to `YES` in the `Planck` class and then `script/repl`.

In order to bundle things up for standalone execution, `script/bundle` and then build it with `useBundledOutput` set to `YES` in the `Planck` class.

# License

Distributed under the Eclipse Public License, which is also used by ClojureScript.
