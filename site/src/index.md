# Planck

Planck is a stand-alone ClojureScript REPL for macOS and Linux.

Planck launches instantly and is useful for scripting.

<img src="img/screenshot.png" style="max-width: 95%;"/>

You can run Clojure-idiomatic scripts with Planck:

```clojure
(require '[planck.core :refer [line-seq with-open]]
         '[planck.io :as io]
         '[planck.shell :as shell])

(with-open [rdr (io/reader "input.txt")]
  (doseq [line (line-seq rdr)]
    (println (count line))))

(shell/sh "say" "done")
```

Get it: On macOS `brew install planck`, on Ubuntu [install](setup.html) using `apt-get`. 

Read the [Planck User Guide](guide.html) or browse the [Planck SDK](sdk.html).

Planck is free and [open source](https://github.com/planck-repl/planck).
