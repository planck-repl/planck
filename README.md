# Planck

A command line bootstrapped ClojureScript REPL for OS X based on JavaScriptCore.

Read the [blog post](http://blog.fikesfarm.com/posts/2015-07-16-fast-javascriptcore-desktop-clojurescript-repl.html).

# Download / Running

You can download the binary file:

[planck.gz](http://blog.fikesfarm.com/planck.gz) size 1169543 bytes, md5 `eacdc5711a941450ae5823aaba221b9b`

# Building 

1. Clone and build [ClojureScript master](https://github.com/clojure/clojurescript) (script/build).
2. Clone [David Nolen's fork of tools.reader](https://github.com/swannodette/tools.reader/tree/cljs-bootstrap), switch to the cljs-bootstrap branch and do lein install.
3. Set the project.clj file so that it matches the ClojureScript master build number.
4. In the ClojureScript/planck directory, do `script/build`
5. Do a pod install in the top level.
6. `open planck.xcworkspace` and adjust the [path](https://github.com/mfikes/planck/blob/master/planck/Planck.m#L29) and run the app

# License

Distributed under the Eclipse Public License, which is also used by ClojureScript.
