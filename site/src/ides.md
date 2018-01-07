## IDEs

### Emacs

Most Clojure developers using Emacs tend to use Cider. Cider needs the
Clojure instance to be running `nRepl`, but Planck doesn't support
that. Planck does instead implement the new Socket REPL capability, but Cider doesn't know how to interact with that.

Luckily for us, [Rich Hickey](http://www.infoq.com/presentations/Simple-Made-Easy)
[thinks Cider is too complex](http://batsov.com/articles/2014/12/04/introducing-inf-clojure-a-better-basic-clojure-repl-for-emacs/),
so 
[Bozhidar Batsov](http://batsov.com) went ahead and created
[inf-clojure](https://github.com/clojure-emacs/inf-clojure).

#### Setup

To set up `inf-clojure` to run with Planck, you can follow the
instructions [here](https://github.com/clojure-emacs/inf-clojure) and
add

```
(setq inf-clojure-program "planck")
```

to your `.emacs` file, given `planck` is on your path. I would be
careful doing

```
(add-hook 'clojure-mode-hook #'inf-clojure-minor-mode)
```
if you use Cider for your other Clojure work, and rather invoke
`inf-clojure` by `M-x inf-clojure-minor-mode` when you're working with 
Planck.

You can now evaluate code directly from your source-code buffer by
pressing `C-x C-e` after the form you want to execute.

### Cursive

It is possible to integrate Cursive with Planck using Planck's Socket REPL capability. To do this, set up a conventional ClojureScript project using, say Leiningen. Then add [Tubular](https://github.com/mfikes/tubular) as a dependency to the project via

```
[tubular "1.0.0"]
```

With this in place, first start up Planck in a regular terminal specifying the `src` directory of your project as Planck's `-c` classpath directive and use `-n` to have Planck listen on a port for Socket REPL sessions. For example:

```
$ planck -c src -n 7777
```

Within Cursive, add a REPL to the project and choose “Use clojure.main in normal JVM process”. Start up the REPL, and issue

```
(require 'tubular.core)
(tubular.core/connect 7777)
```

This will piggyback a Socket REPL session in the Cursive Clojure REPL, and you will see the `cljs.user=>` prompt from Planck. Use the pulldown to switch Cursive's REPL type fro `clj` to `cljs`, and you should be good to go. In particular you can use Cursive's REPL menu option to load files into Planck, sync namespaces, and send forms to the Planck REPL.
