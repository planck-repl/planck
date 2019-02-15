## Internals

<img width="100" align="right" style="margin: 0ex 1em" src="img/internals.jpg">
How does Planck work?

### Fundamentals

At a high level, there is no JVM involved. Planck makes use of ClojureScript's [self-hosting](http://swannodette.github.io/2015/07/29/clojurescript-17) capability and employs JavaScriptCore as its execution environment. JavaScriptCore is the JavaScript engine used by Safari and is already installed on all modern Macs.

When you launch Planck, it internally starts a JavaScriptCore instance and then loads JavaScript implementing the ClojureScript runtime. This JavaScript is [baked into](http://blog.fikesfarm.com/posts/2015-07-27-island-repl.html) the Planck binary.

By default, Planck then starts a REPL. Planck makes entering expressions a little easier by employing [a library](https://github.com/antirez/linenoise), making it possible to edit the line as well as access previously entered lines by using the up arrow. 

Planck enhances this experience by providing tab completion and brace highlighting: 

* When you press the tab key, Planck executes some JavaScript that finds candidate ClojureScript symbols for completions, given what you've currently typed.
* When you type a closing `)`, `]`, or `}` character, Planck executes some JavaScript to find the matching counterpart. If it exists, Planck temporarily moves the cursor over that character. 

(The JavaScript for both of these actions is baked into the binary as well, and is originally sourced from ClojureScript.)

When you enter a complete form, ClojureScript's self-hosting kicks in: The text of the form is passed to the ClojureScript compiler (which is already loaded into JavaScriptCore, pre-compiled as JavaScript). This results in JavaScript that _evaluates_ the form.

The form’s JavaScript is then executed. You can actually see the JavaScript if you start Planck in verbose mode by passing `-v`:

```
$ planck -v
cljs.user=> (+ 2 3)
Evaluating (+ 2 3)
(2 + 3)
5
```

Entering a slightly more complicated expression, you can see that the emitted JavaScript makes use of the ClojureScript runtime:

```
cljs.user=> (first [4 7])
Evaluating (first [4 7])
cljs.core.first.call(null,new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [4,7], null))
4
```

### Side Effects

That's cool when evaluating pure expressions. What about interacting with the outside environment? 

Let's say you want to read the content of a file you have on disk, and you enter these forms:

```clojure
(require '[planck.core :refer [slurp]])
(slurp "foo.txt")
```

At the bottom, Planck has implemented certain I/O primitives and has exposed them as JavaScript functions in JavaScriptCore. One such primitive opens a file for reading. Planck has some code like this

```objectivec
self.inputStream = [NSInputStream
       inputStreamWithFileAtPath:path]
```

in a "file reader" Objective-C class. The constructor for this class is exposed in JavaScript as a function with the name `PLANCK_FILE_READER_OPEN`. This capability is made available to you in ClojureScript by having functions like `slurp` employ ClojureScript code like

```clojure
(js/PLANCK_FILE_READER_OPEN "foo.txt")
```

To actually read from the file, `slurp` calls another `js/PLANCK_FILE_READER_READ` primitive, which invokes code like

```objectivec
[self.inputStream read:buf 
             maxLength:1024]
```

A few Planck ClojureScript namespaces are bundled with Planck in order to provide mappings onto these I/O primitives, exposing the simple APIs—like `slurp`—that you are familiar with: `planck.core`, `planck.io`, and `planck.shell`.

In a nutshell, that’s really a big part of what Planck _is_: Some glue between ClojureScript and the native host environment.

### Affordances

Planck wraps all this with some niceties making it suitable as a scripting environment.

One aspect is the loading of custom ClojureScript source files. Let's say you have `src/my_cool_code/core.cljs`, and at the REPL you invoke

```clojure
(require 'my-cool-code.core)
```

Planck implements the `require` “REPL special form,” which causes bootstrapped ClojureScript—specifically `cljs.js`, via its `*load-fn*`—to load your source from disk (by using Objective-C I/O primitives exposed as JavaScript).

The nice thing is that `*load-fn*` is also used for `:require` specs that may appear in namespace declarations in your code, as  well as `require-macros` and `import`.

To top it off, Planck is free to implement `*load-fn*` in convenient ways: 
* It loads its own namespaces (like `planck.core`) directly from gzipped pre-compiled JavaScript baked in the binary. 
* It also loads code from JAR files: Planck can be provided a classpath, specifying directories and JAR files to be searched when satisfying a load request.

With the ability to dynamically load custom ClojureScript code, executing it by mapping it onto native I/O facilities, can ClojureScript can be used as a compelling alternative for your Bash shell scripting needs.
