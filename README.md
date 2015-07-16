# Planck

A command line bootstrapped ClojureScript REPL for OS X based on JavaScriptCore.

Read the [blog post](http://blog.fikesfarm.com/posts/2015-07-16-fast-javascriptcore-desktop-clojurescript-repl.html).

# Download / Running

You can download the binary file used in the demo in the blog post if you'd like to give it a try. Run the `planck` executeable from within the uncompressed/untarred directory. (The `planck` executable looks for and needs the `planck-cljs-runtime` to be in the current working directory).

[planck-demo.tar.gz](http://blog.fikesfarm.com/planck-demo.tar.gz) size 1195228 bytes, md5 `e5d78cd961f01f40e6162a676fe4b8e3`

# Building 

1. Clone and build ClojureScript master (script/build).
2. Clone David Nolen's fork of tools.reader, switch to the cljs-bootstrap branch and do lein install.
3. Set the project.clj file so that it matches the ClojureScript master build number.
4. In the ClojureScript/planck directory, do lein run -m clojure.main script/build.clj
5. Do a pod install in the top level.
6. open planck.xcworkspace and adjust the [path](https://github.com/mfikes/planck/blob/master/planck/Planck.m#L29) and run the app
