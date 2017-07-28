## REPL

<img width="130" align="right" style="margin: 0ex 1em" src="img/repl.jpg">
If you don't provide any `-i` or `-e` options or args to `planck` when launching it (or if you explicitly specify `-r` or `-​-​repl` as the _main-opt_), Planck will enter an interactive Read-Eval-Print Loop, or _REPL_.

```
$ planck
Planck 2.6.0
ClojureScript 1.9.854
    Docs: (doc function-name-here)
          (find-doc "part-of-name-here")
  Source: (source function-name-here)
    Exit: Control+D or :cljs/quit or exit or quit
 Results: Stored in vars *1, *2, *3, an exception in *e

cljs.user=> ▊
```

To the left of `=>` is the _current namespace_. 

> In ClojureScript, `def` and derived forms create vars in the current namespace. In addition, unqualified non-local symbols are resolved to vars in the current namespace.

You can enter forms to be evaluated in the REPL, and any printed output will be displayed in the REPL, followed by the value of the evaluated form: Try evaluating `(+ 1 2)` and `3` will be printed. Or, try `(println "Hi")` and `Hi` will be printed followed by `nil` (the value of the `println` call).

You can hit return prior to typing a complete form and input will continue on the next line. A `#_=>` prompt will be used (padded to line up with the initial `=>`) for secondary input lines. Multi-line input can continue this way until a full form is entered:

```clojure-repl
cljs.user=> (defn square
       #_=>  [x]
       #_=>  (* x x))
#'cljs.user/square
```

You can enter multiple forms on a line and they will be evaluated serially:

```clojure-repl
cljs.user=> (def x 1) (def y 2) (+ x y)
#'cljs.user/x
#'cljs.user/y
3
```

At any point in entering a form, Ctrl-C can be hit to discard form entry and start with a fresh prompt.

As you type closing delimiters (`)`, `]`, and `}`), the cursor will temporarily hop to the matching opening delimiter.

> If you copy a previously-entered form from the Planck REPL, and paste it back into Planck, any pasted secondary prompts (`#_=>`), as well as the primary namespace prompt, will be detected and elided. (This makes for a cleaner experience when copying and pasting portions of previously-entered large multi-line forms.)

###  Line Editing

#### History

You can use the up and down arrow keys to navigate through previously-entered lines. The line history includes lines entered in previous Planck sessions, with the last 100 lines saved in the `.planck_history` file in your home directory.

You can also type Ctrl-R in order to display a `(reverse-i-search)` prompt: In this mode characters typed perform a live incremental search backwards through history. Pressing Ctrl-R again while in this mode finds additional matches. Once you've found the line you are interested in, you can type Ctrl-J to finish the search and further edit the line. Alternatively, you can hit Ctrl-G to cancel the search. 

#### Tab Completion

You can use the tab key to auto-complete. Try typing `(map` and then hitting the tab key. You will be presented choices like `map-indexed`, `map?`, `mapcat`, _etc._ Hitting shift-tab returns to the originally entered text. Tab completion works aginst core names and also against names you introduce. If you do

```clj
(def supercalifragilisticexpialidocious "something quite atrocious")
```

then `su` followed by tab will yield `subs`, and other choices, including the gem above.

#### Control Keys

Planck employs the line editing library [Linenoise](https://github.com/antirez/linenoise), which provides control characters that you may expect:

* Ctrl-A: Go to beginning of line
* Ctrl-E: Go to end of line
* Ctrl-B: Go back one space
* Ctrl-F: Go forward one space
* Ctrl-K: Delete to end of line
* Ctrl-W: Delete previous word
* Ctrl-H: Delete backwards
* Ctrl-L: Clear screen
* Ctrl-P: Previous line
* Ctrl-N: Next line
* Ctrl-R: Reverse incremental search
* Ctrl-G: Cancel search
* Ctrl-J: Finish search
* Ctrl-T: Transpose characters
* Ctrl-U: Undo typing on line

You may override the set of control characters by creating a `.planck_keymap` file in your home directory that contains a configuration map that looks like:

```clojure
{:go-to-beginning-of-line :ctrl-q
 :delete-previous-word    :ctrl-y}
```

The keys in this map correspond to actions, and the values correspond to the control keys that cause those actions (`:ctrl-a` through `:ctrl-z`).

The set of actions that you can override comprises: `:go-to-beginning-of-line`, `:go-to-end-of-line`, `:go-back-one-space`, `:go-forward-one-space`, `:delete-to-end-of-line`, `:delete-previous-word`, `:delete-backwards`, `:clear-screen`, `:previous-line`, `:next-line`, `:reverse-i-search`, `:cancel-search`, `:finish-search`, `:transpose-characters`, and `:undo-typing-on-line`.

### Result Display

When you evaluate a form at the REPL, the result is pretty printed using [Fipp](https://github.com/brandonbloom/fipp). This causes output to be wrapped and aligned in a manner that makes it easier to see the structure of the data.

> The wrapping honors the width of your terminal, so if you'd like to see a form wrapped differently, resize your terminal and evaluate `*1` to have it re-printed.

If you'd like to turn off pretty printing, just set `*pprint-results*` to `false`:

```
(set! planck.repl/*pprint-results* false)
```

If you evaluate a form that prints lots of output—for example, `(range)`—you can type Ctrl-C to interrupt output printing and return to a fresh prompt.


###  Color Themes

Planck employs various colors for the REPL prompt, results, errors, _etc._ If you'd prefer to work in a monochrome REPL, pass `-t plain` or `-​-theme plain` when starting Planck.

Planck attempts to automatically detect if you are running in a light or dark terminal (first checking and honoring the `COLORFGBG` environment variable, if set) and picks the light or dark theme, which adjusts the colors accordingly. If this detection fails, you can always override it via `-t light` or `-t dark`.

###  Dumb Terminal
Normally, Planck employs the use of VT100 and ANSI codes to perform brace matching, line editing features, and to add color. If you are using Planck in an environment where these codes are not supported, or you would prefer to disable them, you can pass `-d` or `-​-dumb-terminal` when starting Planck.

> If you'd prefer to use Planck with the line-editing capabilities offered by [GNU Readline](http://cnswww.cns.cwru.edu/php/chet/readline/rltop.html), you can use [`rlwrap`](https://github.com/hanslub42/rlwrap), (which is also installable via `brew`). When using `rlwrap`, it is necessary to pass `-d` to `planck` so that `rlwrap`'s terminal controls become active: `rlwrap planck -d`.

###  Exit
You can exit the REPL by typeing Ctrl-D, `exit`, `quit`, or `:cljs/quit`.

### Verbose Mode

If you started Planck in verbose mode (by passing `-v` or `-​-verbose`) then you will see the JavaScript that is executed for forms that you enter in the REPL, along with other useful diagnostic information.

### Quiet Mode

If you started Planck in quiet mode (by passing `-q` or `-​-quiet`) then you will not see any banners from the REPL, just your script output.

### REPL Specials

REPL specials are, in essence, special forms that exist only in the REPL. (They can't be used in regular ClojureScript code and must be entered as top-level forms.)

#### in-ns 

Planck supports `in-ns`, which will switch you to a new namespace, creating it if it doesn't already exist.

```clojure-repl
cljs.user=> (in-ns 'bar.core)
nil
bar.core=> ▊
```

As in Clojure, Planck's `in-ns` REPL special accepts any expression, so long as it evaluates to a symbol, so you can do someting like this

```clojure-repl
cljs.user=> (def my-ns 'foo.core)
#'cljs.user/my-ns
cljs.user=> (in-ns my-ns)
nil
foo.core=> ▊
```

#### load-file
The `load-file` REPL special can be used to load ClojureScript source from any file on the filesystem. It causes the REPL to sequentially read and evaluate the set of forms contained in the file.

### Auto-Referred Symbols

When you launch Planck into REPL mode, a few macros from the `planck.repl` namespace are automatically referred into the `cljs.user` namespace. These comprise `doc`, `source`, `pst`, `apropos`, `find-doc`, and `dir`. 

If you switch to another namespace and find that `doc` no longer works, this is because `doc` is a macro in the `planck.repl` namespace. You can refer it into your current namespace by doing the following: 

```clj
(require '[planck.repl :refer-macros [doc]])
```

The same works for `source`, `pst`, `apropos`, `find-doc`, and `dir`.
