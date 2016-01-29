## Emacs

Most Clojure developers using Emacs tend to use Cider. Cider needs the
Clojure instance to be running `nRepl`, but Planck doesn't do
that. Planck does instead implement the new socket repl, but Cider
doesn't know how to interact with that.

Luckily for us, [Rich Hickey](http://www.infoq.com/presentations/Simple-Made-Easy)
[thinks Cider is too complex](http://batsov.com/articles/2014/12/04/introducing-inf-clojure-a-better-basic-clojure-repl-for-emacs/),
so 
[Bozhidar Batsov](http://batsov.com) went ahead and created
[inf-clojure](https://github.com/clojure-emacs/inf-clojure)

### Setup

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

### Benefits

You can now evaluate code directly from your source-code buffer by
pressing `C-x C-e` after the form you want to execute.
