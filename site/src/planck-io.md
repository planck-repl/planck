# planck.io

Planck I/O functionality.

_Protocols_

[Coercions](#Coercions)<br/>
[IOFactory](#IOFactory)<br/>

_Types_

[File](#File)<br/>

_Vars_

[as-file](#as-file)<br/>
[as-relative-path](#as-relative-path)<br/>
[as-url](#as-url)<br/>
[copy](#copy)<br/>
[delete-file](#delete-file)<br/>
[directory?](#directory?)<br/>
[exists?](#exists?)<br/>
[file](#file)<br/>
[file?](#file?)<br/>
[file-attributes](#file-attributes)<br/>
[file-name](#file-name)<br/>
[hidden-file?](#hidden-file?)<br/>
[input-stream](#input-stream)<br/>
[list-files](#list-files)<br/>
[make-input-stream](#make-input-stream)<br/>
[make-output-stream](#make-output-stream)<br/>
[make-parents](#make-parents)<br/>
[make-reader](#make-reader)<br/>
[make-writer](#make-writer)<br/>
[output-stream](#output-stream)<br/>
[path-elements](#path-elements)<br/>
[reader](#reader)<br/>
[regular-file?](#regular-file?)<br/>
[resource](#resource)<br/>
[symbolic-link?](#symbolic-link?)<br/>
[writer](#writer)<br/>
   
## Protocols

### <a name="Coercions"></a>Coercions
_Protocol_

  Coerce between various 'resource-namish' things.

  `as-file`<br/>
  `([x])`<br/>
  Coerce argument to a [`File`](#File).

  `as-url`<br/>
  `([x])`<br/>
  Coerce argument to a `goog.Uri`.
  
### <a name="IOFactory"></a>IOFactory
_Protocol_

  Factory functions that create ready-to-use versions of
  the various stream types, on top of anything that can
  be unequivocally converted to the requested kind of stream.

  Common options include

  `:append`   true to open stream in append mode<br/>
  `:encoding`  string name of encoding to use, e.g. "UTF-8".

  Callers should generally prefer the higher level API provided by
  [`reader`](#reader), [`writer`](#writer), [`input-stream`](#input-stream), and [`output-stream`](#output-stream).

  `make-reader`<br/>
  `([x opts])`<br/>
  Creates an [`IReader`](planck-core.html#IReader). See also `IOFactory` docs.

  `make-writer`<br/>
  `([x opts])`<br/>
  Creates an `IWriter`. See also `IOFactory` docs.

  `make-input-stream`<br/>
  `([x opts])`<br/>
  Creates an [`IInputStream`](planck-core.html#IInputStream). See also `IOFactory` docs.

  `make-output-stream`<br/>
  `([x opts])`<br/>
  Creates an [`IOutputStream`](planck-core.html#IOutputStream). See also `IOFactory` docs.

## Types

### <a name="File"></a>File

_Type_

_Fields_: `[path]`

Represents a file.

## Vars

### <a name="as-file"></a>as-file
`([x])`

Coerce argument to a [`File`](#File).

### <a name="as-relative-path"></a>as-relative-path
`([x])`

Take an [`as-file`](#as-file)-able thing and return a string if it is
a relative path, else throws an exception.
  
### <a name="as-url"></a>as-url
`([x])`

Coerce argument to a `goog.Uri`.

### <a name="copy"></a>copy
`([input output & opts])`

Copies input to output. Returns nil or throws an exception.

Input may be an [`IInputStream`](planck-core.html#IInputStream) or [`IReader`](planck-core.html#IReader) created using `planck.io`, `File`, or
string.

Output may be an [`IOutputStram`](planck-core.html#IOutputStream) or `IWriter` created using `planck.io`, or [`File`](#File).

The `opts` arg is included for compatibility with `clojure.java.io/copy`
but ignored. If translating between char and byte representations, UTF-8
encoding is assumed.

Does not close any streams except those it opens itself (on a [`File`](#File)).

Spec<br/>
 _args_: `(cat :input any? :output any? :opts (* any?))`<br/>
 _ret_: `nil?`

### <a name="delete-file"></a>delete-file
`([f])`

Delete file `f`.

Spec<br/>
 _args_: `(cat :f (or :string string? :file file?))`<br/>
 
### <a name="directory?"></a>directory?
`([dir])`

Checks if `dir` is a directory.

Spec<br/>
 _args_: `(cat :dir (or :string string? :file file?))`<br/>
 _ret_: `boolean?`<br/>

### <a name="exists?"></a>exists?
`([f])`

Checks if `f` exists on disk.

Spec<br/>
 _args_: `(cat :f (or :string string? :file file?))`<br/>
 _ret_: `boolean?`<br/>

### <a name="file"></a>file
`([arg] [parent child] [parent child & more])`

Returns a [`File`](#File), passing each arg to [`as-file`](#as-file).  Multiple-arg versions treat the first argument as parent and subsequent 
args as children relative to the parent.

Spec<br/>
 _args_: `(cat :path-or-parent any? :more (* any?))`<br/>
 _ret_: `file?`

### <a name="file?"></a>file?
`([x])`

Returns `true` if `x` is a [`File`](#File).

Spec<br/>
 _args_: `(s/cat :x any?)`<br/>
 _ret_: `boolean?`
  
### <a name="file-attributes"></a>file-attributes
`([path])`

Returns a map containing the attributes of the item at a given `path`.

Spec
 _args_: `(cat :path (nillable? (or :string string? :file file?)))`<br/>
 _ret_: `map?`
  
### <a name="file-name"></a>file-name
`([x])`

Returns the name (the final path element) of `x`.

Spec<br/>
 _args_: `(cat :x (or :string string? :file file?))`<br/>
 _ret_: `string?`<br/>

### <a name="hidden-file?"></a>hidden-file?
`([x])`

Checks if `x` is hidden (name begins with a '.' character).

Spec<br/>
 _args_: `(cat :x (or :string string? :file file?))`<br/>
 _ret_: `boolean?`<br/>

### <a name="input-stream"></a>input-stream
`([x & opts])`

Attempts to coerce its argument into an open [`IInputStream`](planck-core.html#IInputStream).

### <a name="list-files"></a>list-files
`([dir])`

Returns a seq of the [`File`](#File)s in `dir` or `nil` if `dir` is not a directory.

Spec
 _args_: `(cat :dir (or :string string? :file file?))`<br/>
 _ret_: `(coll-of file?)`

### <a name="make-input-stream"></a>make-input-stream
`([x opts])`

Creates an [`IInputStream`](planck-core.html#IInputStream). See also [`IOFactory`](#IOFactory) docs.
  
### <a name="make-output-stream"></a>make-output-stream
`([x opts])`
  
Creates an [`IOutputStream`](planck-core.html#IOutputStream). See also [`IOFactory`](#IOFactory) docs.

### <a name="make-parents"></a>make-parents
`([f & more])`

Given the same arg(s) as for [`file`](#file), creates all parent directories of
the file they represent.

Spec<br/>
 _args_: `(cat :path-or-parent any? :more (* any?))`<br/>
 _ret_: `boolean?`
  
### <a name="make-reader"></a>make-reader
`([x opts])`

Creates an [`IReader`](planck-core.html#IReader). See also [`IOFactory`](#IOFactory) docs.
  
### <a name="make-writer"></a>make-writer
`([x opts])`

Creates an `IWriter`. See also [`IOFactory`](#IOFactory) docs.
  
### <a name="output-stream"></a>output-stream
`([x & opts])`

Attempts to coerce its argument into an open [`IOutputStream`](planck-core.html#IOutputStream).

### <a name="path-elements"></a>path-elements
`([x])`

Returns the path elements of `x` as a sequence.

Spec<br/>
 _args_: `(cat :x (or :string string? :file file?))`<br/>
 _ret_: `(s/coll-of string?)`<br/>


### <a name="reader"></a>reader
`([x & opts])`

Attempts to coerce its argument into an open [`IPushbackReader`](planck-core.html#IPushbackReader).

### <a name="regular-file?"></a>regular-file?
`([f])`

Checks if `f` is a regular file.

Spec<br/>
 _args_: `(cat :f (or :string string? :file file?))`<br/>
 _ret_: `boolean?`<br/>

### <a name="resource"></a>resource
`([n])`

Returns the URI for the named resource, `n`.
  
The resource must be either a JAR resource, a file resource or a "bundled" resource. JARs and files are expressed relative to the classpath while "bundled" resources are the namespaces bundled with Planck and are referred to by reference to the file that contains the namespace, eg. `cljs.test` is `"cljs/test.cljs"`.

Spec<br/>
 _args_: `(cat :n string?)`<br/>
 _ret_: `Uri?`<br/>

### <a name="symbolic-link?"></a>symbolic-link?
`([f])`

Checks if `f` is a symbolic link.

Spec<br/>
 _args_: `(cat :f (or :string string? :file file?))`<br/>
 _ret_: `boolean?`<br/>

### <a name="writer"></a>writer
`([x & opts])`

Attempts to coerce its argument into an open `IWriter`.
