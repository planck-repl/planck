## Running

<img width="70" align="right" style="margin: 0ex 1em" src="img/running.jpg">
You launch Planck by typing `planck` in a terminal.

You can also launch Planck using the `plk` script, which integrates with the [`clojure`](https://clojure.org/guides/getting_started) CLI tool to add support for `deps.edn` and classpath-affecting options such as `-Aalias`.

If you'd like to get help on the command line arguments to Planck, do

```
planck -h
```

or

```
plk -h
```

The command line arguments to Planck are heavily modeled after the ones available for the Clojure REPL. In particular, you can provide zero or more _init-opt_ arguments, followed by an optional _main-opt_ argument, followed by zero or more _arg_ arguments. Planck also accepts _long arguments_ (preceeded by two dashes), just like the Clojure REPL.

### Compile Opts EDN

Many of the command line arguments may also supplied via **edn**, passed via `-co` / `--compile-opts`. Any opts passed via `-co` / `--compile-opts` are merged onto any base opts specified directly by command-line flags.

Also, note that it is possible to configure certain behaviors via `-co` / `--compile-opts` where there doesn't exist a direct command line flag.

Compile opts **edn** may be specified directly on the command line as in

```
plk --compile-opts '{:closure-defines {foo.core "bar"}}'
```

or by specifying a file, where an optional leading `@` means that the file should be read from the classpath as in:

```
plk --compile-opts @/my-compile-opts.edn
```

Options that may be configured via `-co` / `--compile-opts` comprise:

- [:checked-arrays](https://clojurescript.org/reference/compiler-options#checked-arrays)
- [:elide-asserts](https://clojurescript.org/reference/compiler-options#elide-asserts)
- [:fn-invoke-direct](https://clojurescript.org/reference/compiler-options#fn-invoke-direct)
- [:foreign-libs](https://clojurescript.org/reference/compiler-options#foreign-libs)
- [:libs](https://clojurescript.org/reference/compiler-options#libs)
- [:optimizations](https://clojurescript.org/reference/compiler-options#optimizations)
- [:source-map](https://clojurescript.org/reference/compiler-options#source-map)
- [:static-fns](https://clojurescript.org/reference/compiler-options#static-fns)
- [:verbose](https://clojurescript.org/reference/compiler-options#verbose)
