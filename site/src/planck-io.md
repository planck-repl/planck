# planck.io

Planck I/O functionality.

_Protocols_

[Coercions](#Coercions)<br/>
[IOFactory](#IOFactory)<br/>

_Types_

[File](#File)<br/>

_Vars_

[as-file](#as-file)<br/>
[as-url](#as-url)<br/>
[delete-file](#delete-file)<br/>
[directory?](#directory?)<br/>
[file](#file)<br/>
[file?](#file?)<br/>
[file-attributes](#file-attributes)<br/>
[input-stream](#input-stream)<br/>
[make-input-stream](#make-input-stream)<br/>
[make-output-stream](#make-output-stream)<br/>
[make-parents](#make-parents)<br/>
[make-reader](#make-reader)<br/>
[make-writer](#make-writer)<br/>
[output-stream](#output-stream)<br/>
[reader](#reader)<br/>
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
  
### <a name="as-url"></a>as-url
`([x])`

Coerce argument to a `goog.Uri`.

### <a name="delete-file"></a>delete-file
`([f])`

Delete file `f`.

Spec<br/>
 _args_: `(cat :f (or :string string? :file file?))`<br/>
 
### <a name=""></a>directory?
`([dir])`

Checks if `dir` is a directory.

Spec<br/>
 _args_: `(cat :dir (or :string string? :file file?))`<br/>
 _ret_: `boolean?`<br/>
 
### <a name="file"></a>file
`([path] [parent & more])`

Returns a [`File`](#File) for given path.  Multiple-arg
versions treat the first argument as parent and subsequent args as
children relative to the parent.

Spec<br/>
 _args_: `(cat :path-or-parent string? :more (* string?))`<br/>
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
 
### <a name="input-stream"></a>input-stream
`([x & opts])`

Attempts to coerce its argument into an open [`IInputStream`](planck-core.html#IInputStream).

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
 _args_: `(cat :path-or-parent string? :more (* string?))`<br/>
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
  
### <a name="reader"></a>reader
`([x & opts])`

Attempts to coerce its argument into an open [`IPushbackReader`](planck-core.html#IPushbackReader).
  
### <a name="writer"></a>writer
`([x & opts])`

Attempts to coerce its argument into an open `IWriter`.
