## Performance

<img width="85" align="right" style="margin: 0ex 1em" src="img/performance.jpg">
Planck can be very fast, especially since it depends on JavaScriptCore, which is great for minimizing startup latency. This makes it very useable for quickly starting up a REPL or running simple scripts.

### Caching

Planck executes scripts by compiling the ClojureScript to JavaScript for execution in JavaScriptCore. This is done dynamically and is usually very fast.

But, if you have scripts that don't change frequently, or are making use of large libraries, and the ClojureScript is expensive to compile, it may make sense to save the resulting JavaScript so that subsequent script execution can bypass compilation.

This means that if you re-run Planck and use namespaces that have been cached, the JavaScript representing those namespaces is simply loaded into JavaScriptCore. 

To enable compilation caching in Planck, you simply need to pass the `-K` or `-​-​auto-cache` option. This will automatically create a `.planck_cache` directory in the current working directory. (Alternatively, you can specify an existing directory into which Planck can write cache files using `-k` or `-​-​cache`.) 

Here's an example: Let's say you have a foo.cljs script file that you run via

```sh
planck foo.cljs
```

Instead, you can instruct Planck to cache:

```
planck -K foo.cljs
```

The first time you run Planck this way, it will save the results of compilation into `.planck_cache`. Then subsequent executions with `-K` will use the cached results instead.

In addition to caching compiled JavaScript, the associated analysis metadata and source mapping information is cached. This makes it possible for Planck to know the symbols in a namespace, their docstrings, _etc._, without having to consult the original source. And, if an exception occurs, the source mapping info is used in forming stack traces. For additional speed, this cached info is written using Transit.

This caching works for

* top-level files like the example above (in which case it is assumed that the forms are in the `cljs.user` namespace, for caching purposes)
* ClojureScript files in a source directory
* code obtained from JARs

The caching mechanism works whether your are running `planck` to execute a script, or if you are invoking `require` in an interactive REPL session.

Planck uses a (naïve) file timestamp mechanism to know if cache files are stale, and it additionally looks at comments like the following

```
// Compiled by ClojureScript 1.9.946 {:static-fns true, :elide-asserts true}
```

in the compiled JavaScript to see if the files are applicable. If a file can’t be used, it is replaced with an updated copy.

Planck's cache invalidation strategy is _naïve_ because it doesn’t attempt to do sophisticated dependency graph analysis. So, there may be corner cases where you have to manually delete the contents of your cache directory, especially if the cached code involved macroexpansion and macro definitions have changed, for example.

> Planck's caching mechanism is compatible with the static function dispatch and assert mechanisms described below. In short, if you have cached code that does not match the current settings for static functions or asserts, then it will not be eligible for loading and will be replaced with freshly-compiled JavaScript as needed. 

### Function Dispatch

#### :static-fns

Planck supports the `:static-fns` ClojureScript compiler option via the `-s` or `-​-​static-fns` command-line flag.

With `:static-fns` disabled (the default), the generated JavaScript for `(foo 1 2)` will look like 

```js
cljs.user.foo.call(null,1,2)
```

and with it enabled you will get

```js
cljs.user.foo(1,2)
```

David Nolen [commented](https://groups.google.com/forum/m/#!msg/clojurescript/holhVap5Rjc/f9bUE26waakJ) on the differences

> It's an option mostly because of REPL development to allow for redefinition. For example if `:static-fns` `true` we statically dispatch to specific fn arities—but what if you redef to a different set of arities? What if you change the var to store a deftype instance that implements `IFn`. That kind of thing.


> So for compilation `:static-fns` can nearly always be `true`, but for the REPL it's not desirable.


In short, enabling it can lead to performance benefits, being more amenable to inlining, _etc._, but usually you want to leave it turned off during dev.

#### :fn-invoke-direct

Even with `:static-fns` enabled, unknown higher-order functions will be called using the `call` construct described above. You can additionally pass the `-f` or `-​-​fn-invoke-direct` command-line flag to enable the `:fn-invoke-direct` ClojureScript compiler option, which causes such functions to instead be called directly.

An illustrative example is the code emitted for `(defn f [g x] (g x))`. With `:static-fns` disabled, `g.call(null,x)` is emitted for the function body. With `:static-fns` enabled, the emitted code will test determining if `g` is associated with a single-arity static dispatch implementation, and if so, call it, otherwise falling back to `g.call(null,x)`. But, with `:fn-invoke-direct`, the fallback branch will instead involve a direct call `g(x)`.

### Closure Optimizations

You can specify the Closure compiler level to be applied to source loaded from namespaces by using `-O` or `-​-optimizations`. The allowed values are `none`, `whitespace`, and `simple`. (Planck doesn't support whole-program optimization, so `advanced` is not an option.)

Consider this example:

```text
$ planck -q -K -c src --optimizations simple
cljs.user=> (require 'foo.core)
nil
```

When the `require` form above is evaluated, the ClojureScript code is first transpiled to JavaScript, and then Google Closure is applied to that resulting code, using the _simple_ optimizations level. If, for example, you `(set! *print-fn-bodies* true)` and example function Vars in the `foo.core` namespace, you will see that _simple_ optimizations have been applied.

Furthermore, if you have caching enabled (the `-K` option above), then code is cached with the optimization level specified. If you later run Planck with a different optimization level, cached code will be invalided and re-compiled at the new optimization level.

While enabling caching is not required, using optimizations and caching together makes sense, given that Closure optimization can take a bit of time to apply.

### Removing Asserts

ClojureScript allows you to embed runtime assertions into your code. Here is an example of triggering an assert at the Planck REPL:

```clojure
cljs.user=> (assert (= 1 2) "Uh oh!")
Assert failed: Uh oh!
(= 1 2)
```

> Also, if you use pre- and post-conditions in your code, then behind the scenes, these involve the use of `assert`.

By default, the `*assert*` var is set to `true` and calls to `assert` can trigger as illustrated above. But if you set this var to `false` then asserts will not trigger, with the macro expanding to `nil`.

```
cljs.user=> (set! *assert* false)
false
cljs.user=> (assert (= 1 2) "Uh oh!")
nil
```

Note that the `*assert*` var is consulted by the `assert` macro at macroexpansion time. JVM-based ClojureScript does not currently support setting `*assert*` dynamically as is illustrated above. And, while you _can_ set `*assert*` in source code being loaded into bootstrap environments like Planck, it will not affect _that_ code because it is being compiled using the previously-set value for `*assert*`.

> If you are curious, this is because a `set!` call on `*assert*` is not _trapped_ by the compiler as is done for `*unchecked-if*`.

With JVM ClojureScript, asserts are disabled globally via the `:elide-asserts` compiler option. Planck supports the `:elide-asserts` compiler option via the `-a` or `-​-​elide-asserts` command-line flag. This flag simply initializes the `*assert*` var to `false` upon startup.

> Note: If you'd like to disable asserts in some source code that you've already loaded at the Planck REPL, you can first `(set! *assert* false)` and then `require` that namespace passing the `:reload` flag.


