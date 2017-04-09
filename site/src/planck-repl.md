# planck.repl

_Vars_

[\*pprint-results\*](#pprint-results)<br/>
[apropos](#apropos)<br/>
[dir](#dir)<br/>
[doc](#doc)<br/>
[find-doc](#find-doc)<br/>
[pst](#pst)<br/>
[source](#source)<br/>

## Vars

### <a name="pprint-results"></a>\*pprint-results\*

`*pprint-results*` controls whether Planck REPL results are
pretty printed. If it is bound to logical false, results
are printed in a plain fashion. Otherwise, results are
pretty printed.
  
### <a name="apropos"></a>apropos
`([str-or-pattern])`

_Macro_

Given a regular expression or stringable thing, return a seq of all
public definitions in all currently-loaded namespaces that match the
`str-or-pattern`.

### <a name="dir"></a>dir
`([nsname])`

_Macro_

Prints a sorted directory of public vars in a namespace

### <a name="doc"></a>doc
`([sym])`

_Macro_

Prints documentation for a var or special form given its name
  
### <a name="find-doc"></a>find-doc
`([re-string-or-pattern])`

_Macro_

Prints documentation for any var whose documentation or name
contains a match for `re-string-or-pattern`
  
### <a name="pst"></a>pst
`([] [e])`

_Macro_

Prints a stack trace of the exception.

If none supplied, uses the root cause of the most recent repl exception (`*e`)
  
### <a name="source"></a>source
`([sym])`

_Macro_

Prints the source code for the given symbol, if it can find it.
This requires that the symbol resolve to a Var defined in a
namespace for which the source is available.

Example: `(source filter)`
  