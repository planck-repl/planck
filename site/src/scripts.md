## Scripts

<img width="100" align="right" style="margin: 0ex 1em" src="img/scripts.jpg">
Planck can be used to run scripts written in ClojureScript. Planck and JavaScriptCore are fast to start up, and the ClojureScript reader and compiler have been optimized for bootstrapped mode, making this a perfectly feasible approach. It makes for a great alternative for shell scripts written in, say, Bash.

Perhaps the simplest way to execute a script with Planck is to create a file and to use `planck` to run it. For example, say you have `foo.cljs` with

```
(println "Hello World!")
```

Then you can execute it:

```
$ planck foo.cljs
Hello World!
```

### Standalone Scripts

What if you'd like to make a standalone executable? The Clojure reader treats `#!` as a line comment, supporting the use of shebang scripts. You can change `foo.cljs` to look like

```
#!/usr/bin/env planck
(println "Hello World!")
```

and then if you first set the executable bit, you can execute the file directly:

```
$ chmod +x foo.cljs 
$ ./foo.cljs 
Hello World!
```

```
$ planck bar.cljs there
Hello there!
```

### Main Function

If you'd like your script to start execution by executing a main function, you can make use of Planck's `-m` command-line option, specifying the namespace containing a `-main` function. Let's say you have `foo/core.cljs` with:

```
(ns foo.core)

(defn greet [name]
  (println (str "Hello " name "!")))

(defn -main [name]
  (greet name))
```

then this works:

```
$ planck -m foo.core ClojureScript
Hello ClojureScript!
```

### Command Line Arguments

If you'd like to gain access to the command line arguments passed to your script, they are available in `planck.core/*command-line-args*` (mimicking the behavior of `clojure.core/*command-line-args*` when writing scripts with Clojure).

With `bar.cljs`:

```
(ns bar.core
  (:require [planck.core :refer [*command-line-args*]]))

(println (str "Hello " (first *command-line-args*) "!"))
```

#### Argument Processing

You can use the [`clojure.tools.cli`](https://github.com/clojure/tools.cli) library to parse command line options. Here is the intro example being used with Planck:

```
$ planck -c tools.cli-0.3.5.jar -m my.program -vvvp8080 foo --help --invalid-opt
{:errors ["Unknown option: \"--invalid-opt\""],
 :arguments ["foo"],
 :summary "  -p, --port PORT  80  Port number\n  -v                   Verbosity level\n  -h, --help",
 :options {:port 8080, :verbosity 3, :help true}}
```

where `my/program.cljs` contains:

```clojure
(ns my.program
  (:require [clojure.tools.cli :refer [parse-opts]]
            [fipp.edn :refer [pprint]]))

(def cli-options
  ;; An option with a required argument
  [["-p" "--port PORT" "Port number"
    :default 80
    :parse-fn #(js/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ;; A non-idempotent option
   ["-v" nil "Verbosity level"
    :id :verbosity
    :default 0
    :assoc-fn (fn [m k _] (update-in m [k] inc))]
   ;; A boolean option defaulting to nil
   ["-h" "--help"]])

(defn -main [& args]
  (pprint (parse-opts args cli-options)))
```

### Shell Interaction

The `planck.shell` namespace provides functions for interacting with the shell.
Commands can be executed by running the `sh` function as seen in the following example:

```
#!/usr/bin/env planck
(ns foo.core
  (:require [planck.shell :refer [sh]]
            [planck.core :refer [*command-line-args*]]))

(defn list-files [dir]
  (println "listing files in" dir)
  (println (sh "ls" "-l" dir)))

(list-files (first *command-line-args*))
```
