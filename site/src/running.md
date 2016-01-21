## Running

<img width="70" align="right" style="margin: 0ex 1em" src="img/running.jpg">
You launch Planck by typing `planck` in a terminal.

If you'd like to get help on the command line arguments to Planck, do

```
planck -h
```

The command line arguments to Planck are heavily modeled after the ones available for the Clojure REPL. In particular, you can provide zero or more _init-opt_ arguments, followed by an optional _main-opt_ argument, followed by zero or more _arg_ arguments.

> Note that, Planck accepts _long arguments_ (preceeded by two dashes), just like the Clojure REPL, but with Planck there must be an equals sign between the argument name and value.
