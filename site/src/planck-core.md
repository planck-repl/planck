# planck.core

Core Planck functions for use in scripts.

_Protocols_

[IBufferedReader](#IBufferedReader)<br/>
[IClosable](#IClosable)<br/>
[IInputStream](#IInputStream)<br/>
[IOutputStream](#IOutputStream)<br/>
[IPushbackReader](#IPushbackReader)<br/>
[IReader](#IReader)<br/>

_Vars_

[\*command-line-args\*](#command-line-args)<br/>
[\*err\*](#err)<br/>
[\*in\*](#in)<br/>
[\*planck-version\*](#planck-version)<br/>
[eval](#eval)<br/>
[exit](#exit)<br/>
[file-seq](#file-seq)<br/>
[find-var](#find-var)<br/>
[init-empty-state](#init-empty-state)<br/>
[intern](#intern)<br/>
[line-seq](#line-seq)<br/>
[load-reader](#load-reader)<br/>
[load-string](#load-string)<br/>
[ns-resolve](#ns-resolve)<br/>
[read](#read)<br/>
[read-line](#read-line)<br/>
[read-password](#read-password)<br/>
[read-string](#read-string)<br/>
[requiring-resolve](#requiring-resolve)<br/>
[resolve](#resolve)<br/>
[sleep](#sleep)<br/>
[slurp](#slurp)<br/>
[spit](#spit)<br/>
[with-open](#with-open)<br/>

## Protocols

### <a name="IBufferedReader"></a>IBufferedReader
_Protocol_

Protocol for reading line-based content. Instances of `IBufferedReader` must
   also satisfy [`IReader`](#IReader).

  `-read-line`<br/>
  `([this])`<br/>
  Reads the next line.


### <a name="IClosable"></a>IClosable
_Protocol_

Protocol for closing entities.

  `-close`<br/>
  `([this])`<br/>
  Closes this entity.

### <a name="IInputStream"></a>IInputStream
_Protocol_

  Protocol for reading binary data.

  `-read-bytes`<br/>
  `([this])`<br/>
  Returns available bytes as an array of unsigned numbers or `nil` if EOF.

### <a name="IOutputStream"></a>IOutputStream
_Protocol_

  Protocol for writing binary data.

  `-write-bytes`<br/>
  `([this byte-array])`<br/>
  Writes byte array.

  `-flush-bytes`<br/>
  `([this])`<br/>
  Flushes output.

### <a name="IPushbackReader"></a>IPushbackReader
_Protocol_

Protocol for readers that support undo. Instances of `IPushbackReader` must
  also satisfy [`IBufferedReader`](#IBufferedReader).

  `-unread`<br/>
  `([this s])`<br/>
  Pushes a string of characters back on to the stream.
  
### <a name="IReader"></a>IReader
_Protocol_

  Protocol for reading.

  `-read`<br/>
  `([this])`<br/>
  Returns available characters as a string or `nil` if EOF.

## Vars

### <a name="command-line-args"></a>\*command-line-args\*

A sequence of the supplied command line arguments, or `nil` if none were supplied

### <a name="err"></a>\*err\*

A `cljs.core/IWriter` representing standard error for print operations.

### <a name="in"></a>\*in\*
  An [`IReader`](#IReader) representing standard input for read operations.

### <a name="planck-version"></a>\*planck-version\*

A string containing the version of the Planck executable.

### <a name="eval"></a>eval
`([form])`

Evaluates the form data structure (not text!) and returns the result.

Spec<br/>
 _args_: `(cat :form any?)`<br/>
 _ret_: `any?`<br/>

### <a name="exit"></a>exit
`([exit-value])`

Causes Planck to terminate with the supplied `exit-value`.

Spec<br/>
 _args_: `(cat :exit-value integer?)`<br/>

### <a name="file-seq"></a>file-seq
`([dir])`

A tree seq on files

Spec<br/>
 _args_: `(cat :dir (or :string string? :file file?))`

### <a name="find-var"></a>find-var
`([sym])`

Returns the global var named by the namespace-qualified symbol, or
`nil` if no var with that name.

Spec<br/>
 _args_: `(cat :sym qualified-symbol?)`<br/>
 _ret_: `(nilable var?)`

### <a name="init-empty-state"></a>init-empty-state
`([state])`

An init function for use with `cljs.js/empty-state` which initializes
the empty state with `cljs.core` analysis metadata.

This is useful because Planck is built with `:dump-core` set to false.

Usage: `(cljs.js/empty-state init-empty-state)`

Spec<br/>
 _args_: `(cat :state map?)`<br/>
 _ret_: `map?`<br/>

### <a name="intern"></a>intern
`([ns name] [ns name val])`

Finds or creates a var named by the symbol `name` in the namespace
`ns` (which can be a symbol or a namespace), setting its root binding
to `val` if supplied. The namespace must exist. The var will adopt any
metadata from the `name` symbol.  Returns the var.

Spec<br/>
 _args_: `(cat :ns (or :sym symbol? :ns #(instance? Namespace %)) :name symbol? :val (? any?))`<br/>

### <a name="line-seq"></a>line-seq
`([rdr])`

Returns the lines of text from rdr as a lazy sequence of strings.
`rdr` must implement [`IBufferedReader`](#IBufferedReader).

Spec<br/>
 _args_: `(cat :rdr (instance? IBufferedReader %))`<br/>
 _ret_: `seq?`<br/>

### <a name="load-reader"></a>load-reader
`([rdr])`

  Sequentially read and evaluate the set of forms contained in the
  stream/file

Spec<br/>
 _args_: `(cat :reader (satisfies? IPushbackReader %))`<br/>
 _ret_: `any?`

### <a name="load-string"></a>load-string
`([rdr])`

  Sequentially read and evaluate the set of forms contained in the
  string

Spec<br/>
 _args_: `(cat :s string?)`<br/>
 _ret_: `any?`

### <a name="ns-resolve"></a>ns-resolve
`([ns sym])`

Returns the var to which a symbol will be resolved in the namespace,
else `nil`.

Spec<br/>
 _args_: `(cat :ns symbol? :sym symbol?)`<br/>
 _ret_: `(nilable var?)`<br/>

### <a name="read"></a>read
`([] [reader] [opts reader] [reader eof-error? eof-value])`

  Reads the first object from a [`IPushbackReader`](#IPushbackReader).
  Returns the object read. If EOF, throws if `eof-error?` is `true`.
  Otherwise returns sentinel. If no reader is provided, [`*in*`](#in) will be used.
  Opts is a persistent map with valid keys:
  
  `:read-cond` - `:allow` to process reader conditionals, or `:preserve` to keep all branches
              
  `:features` - persistent set of feature keywords for reader conditionals
  
  `:eof` - on eof, return value unless `:eofthrow`, then throw. if not specified, will throw

Spec<br/>
 _args_:  `(alt :nullary (cat ) :unary (cat :reader #(satisfies? IPushbackReader %)) :binary (cat :opts map? :reader #(satisfies? IPushbackReader %)) :ternary (cat :reader #(satisfies? IPushbackReader %) :eof-error? boolean? :eof-value any?))`

### <a name="read-line"></a>read-line
`([])`

  Reads the next line from the current value of [`*in*`](#in)

Spec<br/>
 _args_: `(cat )`<br/>
 _ret_: `string?`<br/>

### <a name="read-password"></a>read-password
`([] [prompt])`

  Reads the next line from console with echoing disabled.
  It will print out a prompt if supplied

Spec<br/>
 _args_: `(cat :prompt (? string?))`<br/>
 _ret_: `string?`<br/>
 
### <a name="read-string"></a>read-string
`([s] [opts s])`

  Reads one object from the string `s`. Optionally include reader
  options, as specified in [`read`](#read).

Spec<br/>
 _args_: `(alt :unary (cat :s string?) :binary (cat :opts map? :s string?))`<br/><br/>

### <a name="requiring-resolve"></a>requiring-resolve
`([sym])`

  Resolves namespace-qualified sym per [`resolve`](#resolve). If initial resolve
  fails, attempts to `require` `sym`'s namespace and retries.

Spec<br/>
 _args_: `(cat :sym qualified-symbol?)`<br/>
 _ret_: `(nilable var?)`<br/>
 
### <a name="resolve"></a>resolve
`([sym])`

  Returns the var to which a symbol will be resolved in the current
  namespace, else `nil`.

Spec<br/>
 _args_: `(cat :sym symbol?)`<br/>
 _ret_: `(nilable var?)`<br/>

### <a name="sleep"></a>sleep
`([f & opts])`

  Causes execution to block for the specified number of milliseconds plus the
  optionally specified number of nanoseconds.

  `millis` should not be negative and `nanos` should be in the range 0–999999

Spec<br/>
 _args_: `(alt :unary (cat :millis #(and (integer? %) (not (neg? %)))) :binary (cat :millis #(and (integer? %) (not (neg? %))) :nanos #(and (integer? %) (<= 0 % 999999))))`<br/>

### <a name="slurp"></a>slurp
`([f & opts])`

  Opens a reader on `f` and reads all its contents, returning a string.
  See [`planck.io/reader`](planck-io.html#reader) for a complete list of supported arguments.

Spec<br/>
 _args_: `(cat :f (or :string string? :file file?) :opts (* any?))`<br/>
 ret: `string?`

### <a name="spit"></a>spit
`([f content & opts])`

  Opposite of `slurp`.  Opens `f` with [`writer`](planck-io.html#writer), writes content, then
  closes `f`. Options passed to [`planck.io/writer`](planck-io.html#writer).

Spec<br/>
 `args`: `(cat :f (or :string string? :file file?) :content any? :opts (* any?))`

### <a name="with-open"></a>with-open
`([bindings & body])`

_Macro_

  `bindings` => `[name IClosable `...`]`

  Evaluates `body` in a `try` expression with names bound to the values
  of the inits, and a `finally` clause that calls `(-close name)` on each
  `name` in reverse order.
