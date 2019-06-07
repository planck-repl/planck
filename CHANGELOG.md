# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]
### Added
- Native temp file and dir facilities ([#934](https://github.com/planck-repl/planck/issues/934)

### Changed
- `io/reader` on http follow redirects by default ([#937](https://github.com/planck-repl/planck/issues/937))
- Update dependencies ([#940](https://github.com/planck-repl/planck/issues/940), [#941](https://github.com/planck-repl/planck/issues/941), [#942]((https://github.com/planck-repl/planck/issues/942))

### Fixed
- `tty?` spec refers to `planck.core/IWriter` ([#928](https://github.com/planck-repl/planck/issues/928))
- HTTP binary response includes trailing buffer ([#949](https://github.com/planck-repl/planck/issues/949))

## [2.23.0] - 2019-05-19
### Added
- Add `planck.io/tty?` ([#911](https://github.com/planck-repl/planck/issues/911))
- Add -keep-gcl option to the clean script
- Ignore `*.swp` files
- Support [`NO_COLOR`](https://no-color.org) ([#923](https://github.com/planck-repl/planck/issues/923))
- Auto complete for `#queue`, `#inst`, _etc_. ([#926](https://github.com/planck-repl/planck/issues/926))

### Changed
- Re-enable FTL JIT on macOS ([#820](https://github.com/planck-repl/planck/issues/820))
- Updated planck-repl.org to use HTTPs and revised all references.

### Fixed
- Switch `strncpy` to `memcpy` to avoid GCC warning
- Backslash return return should produce "\n" ([661](https://github.com/planck-repl/planck/issues/661))
- `-e` affects `*1`, `*2`, `*3` ([#659](https://github.com/planck-repl/planck/issues/659))
- Print queues properly ([#921](https://github.com/planck-repl/planck/issues/921))

## [2.22.0] - 2019-04-06
### Added
- Add `planck.core/with-in-str` ([#873](https://github.com/planck-repl/planck/issues/873))
- Document `planck.io/resource` ([#487](https://github.com/planck-repl/planck/issues/487))
- Support `ns-aliases` and `ns-refers` ([#505](https://github.com/planck-repl/planck/issues/505))
- Add almost all of the Google Closure Library ([#496](https://github.com/planck-repl/planck/issues/496))
- Bundle official distribution of Google Closure Library
- Add caching of optimisation files

### Changed
- Update to Google Closure Library v20190301
- Update to Google Closure Compiler v20190301

### Fixed
- Execution error instead of syntax for `08` ([#857](https://github.com/planck-repl/planck/issues/857))
- Support interning quoted data structues ([#508](https://github.com/planck-repl/planck/issues/508))
- Fix error messages in `planck.io` ([#582](https://github.com/planck-repl/planck/issues/582))
- Bad import taints analysis metadata ([#888](https://github.com/planck-repl/planck/issues/888))
- Long form needed with plk for optimizations / checked arrays ([#750](https://github.com/planck-repl/planck/issues/750))
- Crash when defining single segment ns ([#588](https://github.com/planck-repl/planck/issues/588))
- Handle interruptions while nanosleeping ([#909](https://github.com/planck-repl/planck/issues/909))

## [2.21.0] - 2019-03-07
### Added
- Add `planck.io/exists?`, `planck.io/hidden-file?`, `planck.io/regular-file?`, `planck.io/symbolic-link?` ([#863](https://github.com/planck-repl/planck/issues/863))
- Add `planck.io/path-elements`, `planck.io/file-name`
- Document using Boot to generate a classpath file ([#343](https://github.com/planck-repl/planck/issues/343))
- Crash with empty HTTP response header ([#894](https://github.com/planck-repl/planck/issues/894))

### Changed
* Update to ClojureScript 1.10.520

### Fixed
- Consistent use of 'accept' in `planck.http` ([#837](https://github.com/planck-repl/planck/issues/837))
- `planck.core/load-string` evaluates in current namespace ([#867](https://github.com/planck-repl/planck/issues/867))

## [2.20.0] - 2019-01-31
### Added
- Option to have `planck.http` follow redirects ([#842](https://github.com/mfikes/planck/issues/842))
- Add `requiring-resolve` ([#835](https://github.com/mfikes/planck/issues/835))
- Support setting user agent in `planck.http` ([#838](https://github.com/mfikes/planck/issues/838))
- Bundle new datafy namespace ([#851](https://github.com/mfikes/planck/issues/851))
- Support for improvements to exception messages and printing in ClojureScript 1.10.516 ([#845](https://github.com/mfikes/planck/issues/845))

### Changed
- Update to ClojureScript 1.10.516
- Update to Fipp 0.6.14
- Syntax-highlight and shorten qualified keywords in default spec explain printer ([#848](https://github.com/mfikes/planck/issues/848))

### Fixed
- Make s/explain provide names of core fns ([#832](https://github.com/mfikes/planck/issues/832))
- Analysis cache issue with `load-file` ([#843](https://github.com/mfikes/planck/issues/843))
- Check for handle undefined values in C code that fetches values from JavaScriptCore
- Fix `fnil` expression: `0` for `nil` in second position ([#854](https://github.com/mfikes/planck/issues/854))
- Ensure `cljs.js/require` called with bound vars ([#855](https://github.com/mfikes/planck/issues/855))


## [2.19.0] - 2018-11-02
### Changed
- Update to ClojureScript 1.10.439
- Support for `:spec-skip-macros` compiler option ([#806](https://github.com/planck-repl/planck/issues/806))
- Enhanced `delay` printing ([#827](https://github.com/mfikes/planck/issues/827))

## [2.18.1] - 2018-10-23
### Changed
- Pretty-print atoms and volatiles

### Fixed
- Requiring foreign libs with cljsjs broken ([#825](https://github.com/mfikes/planck/issues/825))

## [2.18.0] - 2018-10-13
### Added
- Add support for tagged literals ([#517](https://github.com/mfikes/planck/issues/517))
- `doc` for spec-registered keywords
- Support for passing options via `--compile-opts` ([#774](https://github.com/planck-repl/planck/issues/774))
- Support for `:closure-defines` ([#773](https://github.com/planck-repl/planck/issues/773))
- Support for `:source-map` compiler option
- Support for `:warnings` compiler option ([#783](https://github.com/planck-repl/planck/issues/783))
- Support for `:repl-requires` compiler option ([#786](https://github.com/planck-repl/planck/issues/786))
- Support for `:def-emits-var` compiler option ([#785](https://github.com/planck-repl/planck/issues/785))
- Auto-load `user.`(`cljs`|`cljc`) ([#754](https://github.com/planck-repl/planck/issues/754))
- Allow `planck.shell` `:in` to take same as `io/copy` ([#808](https://github.com/planck-repl/planck/issues/808))
- Add `find-var` imitation ([#743](https://github.com/planck-repl/planck/issues/743))
- Add `planck.io/list-files` ([#748](https://github.com/planck-repl/planck/issues/748))
- Add `planck.core/load-reader` and `planck.core/load-string` ([#687](https://github.com/planck-repl/planck/issues/687))

### Changed
- Updates for private var use in Planck namespaces
- Ensure all earmuffed vars defined in Planck namespaces are dynamic
- Support `IPrintWithWriter` on native types ([#767](https://github.com/planck-repl/planck/issues/767))
- Use Number.isInteger if possible ([#799](https://github.com/planck-repl/planck/issues/799))
- Monkey patch target-specific array? and find-ns-obj ([#798](https://github.com/planck-repl/planck/issues/798))
- Update `test.check` to 0.10.0-alpha3 ([#802](https://github.com/planck-repl/planck/issues/802))

### Fixed
- Cache behavior when executing standalone script with shebang deps ([#749](https://github.com/mfikes/planck/issues/749))
- Fix build issue when using `xxdi.pl` ([#770](https://github.com/mfikes/planck/issues/770))
- Fix loading of Closure libs
- Trailing comma error with optimizations ([#781](https://github.com/mfikes/planck/issues/781))
- Log errors when initializing engine ([#787](https://github.com/mfikes/planck/issues/787))
- Bundle missing macros namespaces ([#794](https://github.com/planck-repl/planck/issues/794))
- ClojureScript version should not be displayed if there are inits ([#804](https://github.com/planck-repl/planck/issues/804))
- Docstring for `planck.shell/sh` `:in` should indicate `string` ([#807](https://github.com/planck-repl/planck/issues/807))
- Faster `array`<->`vec` I/O ([#812](https://github.com/planck-repl/planck/issues/812))
- Faster `file-seq` ([#816](https://github.com/planck-repl/planck/issues/816))

## [2.17.0] - 2018-07-02
### Changed
- Update to ClojureScript 1.10.339
- Delegate to ClojureScript `deps.edn` for `transit-clj`

### Fixed
- Crash getting http response via socket REPL ([#760](https://github.com/mfikes/planck/issues/760))

## [2.16.0] - 2018-06-22
### Changed
- Update to ClojureScript 1.10.329

## [2.15.0] - 2018-06-15
### Added
- Read the environment variables into `planck.environ/env` ([#751](https://github.com/mfikes/planck/issues/751))
### Changed
- Update to ClojureScript 1.10.312
- Update to Closure Compiler v20180610
- No longer bundle `goog.json.EvalJsonProcessor` in order to support latest Closure Library.
- Accommodate new Closure Library deps management.

### Fixed
- If you pass a non-fn to setTimeout it should throw ([#702](https://github.com/mfikes/planck/issues/702))

## [2.14.0] - 2018-05-02
### Added
- Document how to use `-Sdeps` in shebang on macOS ([#720](https://github.com/mfikes/planck/issues/720))
- Man pages for `planck` and `plk` ([#727](https://github.com/mfikes/planck/issues/727))
- A `script/install` that will install `planck`, `plk`, and man pages ([#728](https://github.com/mfikes/planck/issues/728))
- Document shutdown semantics ([#723](https://github.com/planck-repl/planck/issues/723))

### Changed
- Default FTL JIT to off ([#722](https://github.com/mfikes/planck/issues/722))
- Avoid private var use within Planck code to prepare for [CLJS-1702](https://dev.clojure.org/jira/browse/CLJS-1702)
- Improve "launch path not accessible" message from `planck.shell/sh` ([#721](https://github.com/mfikes/planck/issues/721))

### Fixed
- If `exit` called, exit immediately ([#735](https://github.com/mfikes/planck/issues/735))
- Need to load REPL code when initializing engine ([#737](https://github.com/mfikes/planck/issues/737))

## [2.13.0] - 2018-04-17
### Added
- Add a new `plk` script which delegates to `clojure` for `deps.edn`, _etc._ ([#710](https://github.com/mfikes/planck/issues/710))
- Port `clojure.java.io/copy` ([#677](https://github.com/mfikes/planck/issues/677))

### Fixed
- `js/clearTimeout` should allow shutdown ([#698](https://github.com/mfikes/planck/issues/698))

### Changed
- Update to `transit-cljs` 0.8.248 and remove workaround introduced with [#647](https://github.com/mfikes/planck/issues/647)
- Update to Closure v20180319
- Update help for `*command-line-args*` ([#615](https://github.com/mfikes/planck/issues/615))
- On macOS 10.13.4 and later default FTL JIT to off ([#706](https://github.com/planck-repl/planck/issues/706))

## [2.12.6] - 2018-03-26
### Fixed
- Fixed issues with building Ubuntu PPA in sandbox environment
- Fixed implicit definition of `execvpe` when compiling for Linux

## [2.12.0] - 2018-03-25
### Added
- Support `js/clearTimeout`, `js/setInterval`, and `js/clearInterval` ([#127](https://github.com/mfikes/planck/issues/597))
- Add a NixOS 17.09 build environment
- Include informative message about `-fast` when building ([#587](https://github.com/mfikes/planck/issues/587))
- Allow HTTP response body to optionally be encoded as binary ([#649](https://github.com/mfikes/planck/issues/649))
- Added `io/make-parents` ([#470](https://github.com/mfikes/planck/issues/470))
- Added `io/as-relative-path`
- Support classpath-relative paths (starting with `@` or `@/`) for `-i` and script paths 
- Support for insecure http connections ([#694](https://github.com/mfikes/planck/issues/694))

### Changed
- Use `clojure` / `deps.edn` instead of `lein` / `project.clj` when building
- Tweaks to build process supporting depending on a ClojureScript source tree (instead of JAR)
- Update to Closure v20180204
- Avoid consequences enable-console-print! in core ([#638](https://github.com/mfikes/planck/issues/638))
- Truncate stacktraces to stop in user code
- Use a simpler startup banner matching ClojureScript
- Update `planck.core/eval` to delegate to the new `cljs.core/eval`

### Fixed
- In stacktraces, JavaScript sources assumed to be ClojureScript ([#624](https://github.com/mfikes/planck/issues/624))
- Bundled JavaScript files not source-mapped ([#630](https://github.com/mfikes/planck/issues/630))
- Fully qualified name lost in stacktrace w/optimization ([#635](https://github.com/mfikes/planck/issues/635))
- Incorrect stacktrace demunging with hyphens ([#641](https://github.com/mfikes/planck/issues/641))
- Sometimes async prints missing ([#655](https://github.com/mfikes/planck/issues/655))
- `clojure.reflect` not bundled ([#667](https://github.com/mfikes/planck/issues/667))
- Remove unnecessary equals signs between options and parameter values in help output
- Provide more info if sh err is 126 or 127 ([#673](https://github.com/mfikes/planck/issues/673))
- Degenerate PATH if any env map supplied to `planck.shell/sh` ([#672](https://github.com/mfikes/planck/issues/672))
- `io/file` should make use of `io/as-file` internally ([#683](https://github.com/mfikes/planck/issues/683))

## [2.11.0] - 2018-01-23
### Added
- Add `M-f`, `M-b`, and `M-d` support for REPL ([#569](https://github.com/mfikes/planck/issues/569))
- Add support for core.specs.alpha ([#592](https://github.com/mfikes/planck/issues/592))
- HTTP facility supports UNIX sockets ([#597](https://github.com/mfikes/planck/issues/597))
- Update Dependencies page showing how to use deps.edn ([#575](https://github.com/mfikes/planck/issues/575))

### Changed
- Use Clojure 1.9.0 when building Planck
- Use FindCurl when building Planck ([#598](https://github.com/mfikes/planck/issues/598))

### Fixed
- `with-sh-env` codepath subjects `env` to spec validation ([#565](https://github.com/mfikes/planck/issues/565))
- Improve perf of loading resources from JARs ([#566](https://github.com/mfikes/planck/issues/566))
- Update site docs for tab completion ([#545](https://github.com/mfikes/planck/issues/545))
- Auto-completion fails with numbers in ns names ([#578](https://github.com/mfikes/planck/issues/578))
- Terminal size changes not detected until line entered ([#584](https://github.com/mfikes/planck/issues/584))
- Odd jump if you paste long line ([#459](https://github.com/mfikes/planck/issues/459))
- Don't use sequence to print eduction ([#590](https://github.com/mfikes/planck/issues/590))
- Can't make trivial revisions to `function_http_request` without `SIGSEGV`s ([#600](https://github.com/mfikes/planck/issues/600))

## [2.10.0] - 2017-12-07
### Added
- Site docs for `--fn-invoke-direct` ([#547](https://github.com/mfikes/planck/issues/547))
- Implement http put, patch, delete, head ([#548](https://github.com/mfikes/planck/issues/548))
- Make `dir` work on aliases ([#552](https://github.com/mfikes/planck/issues/552))
- Add `planck.core/sleep` ([#558](https://github.com/mfikes/planck/issues/558))
- Add `planck.core/read` ([#560](https://github.com/mfikes/planck/issues/560))
- Add `planck.core/read-string` ([#559](https://github.com/mfikes/planck/issues/559))

### Changed
- Update build to use Lein 2.8.1
- Eliminate doc site reference to `:static-fns` as a workaround for (fixed) JavaScriptCore perf bug.
- Update to Closure v20170910
- Update build to allow alternate `xxd -i` implementation ([#549](https://github.com/mfikes/planck/issues/549))
- Update doc site to reflect that `cljs.core/*command-line-args*` is populated.
- Update doc site Dependencies Foreign Libs CLJSJS section to use `boot`
- Use `:foreign-libs` `:file-min` if optimizations `simple` ([#555](https://github.com/mfikes/planck/issues/555))

### Removed
- Remove `planck.core` types meant to be private ([#562](https://github.com/mfikes/planck/issues/562))

### Fixed
- Single-dash command line args not passed to script ([#550](https://github.com/mfikes/planck/issues/550))
- `this` bound to `planck.repl` when foreign lib loaded ([#554](https://github.com/mfikes/planck/issues/554))
- Auto-complete for referred Vars ([#556](https://github.com/mfikes/planck/issues/556))
- Clear EOF after reading file so subsequent read calls will see any appended data ([#557](https://github.com/mfikes/planck/issues/557))
- `source` fails on Vars whose source has ns-aliased keywords ([#561](https://github.com/mfikes/planck/issues/561))

## [2.9.0] - 2017-11-14
### Changed
- transit-cljs 0.8.243
- Show completion candidates when hitting tab ([#527](https://github.com/mfikes/planck/issues/527))
- Remove `planck.repl/get-arglists` spec (it is non-user supplied and present by default)

### Fixed
- Fix issue where source/doc would work on private planck.repl Vars ([#542](https://github.com/mfikes/planck/issues/542))
- Fix issue where global a/b/c shadowed by locals ([#543](https://github.com/mfikes/planck/issues/543))
- Eliminate stale reference to `coercible-file?` spec ([#544](https://github.com/mfikes/planck/issues/544))

## [2.8.1] - 2017-10-03
### Fixed
- Fix Linux PPA build issue with build box home dir

## [2.8.0] - 2017-10-03
### Added
- Optimizations for source map loading when first exception is printed.
- Honor `cljs.core/*main-cli-fn*`, calling if set.
- Facsimile of `cljs.nodejs` for code calling `enable-util-print!`.

### Changed
- ClojureScript 1.9.946.

### Fixed
- Eliminate leaks and properly initialize memory.
- `planck.repl/get-arglists` now resolves symbols in current namespace.
- It is now possible to require `goog`.
- Fix SIGSEGV with glibc 2.26.

## [2.7.3] - 2017-08-17
### Fixed
- Fix issue with Ubuntu PPA release.

## [2.7.0] - 2017-08-16
### Added
- Ability to specify `-O` / `--optimizations` to apply Closure.
- Closure applied to bundled deps.
- Pretty print records.
- Update deps management for global exports, `:libs`.
- Auto-completion for symbols in Closure Library.

### Changed
- ClojureScript 1.9.908.
- Add checked-arrays to `-h` output.
- No longer bundle `goog.structs.weak` (not available).

### Fixed
- Disable `:def-emits-var` in code-loading forms.
- Increas buffers used for paths to be `PATH_MAX`.

## [2.6.0] - 2017-07-28
### Added
- Alpha support for TCP sockets in `planck.socket.alpha`.
- Support passing Maven coordinates for JAR deps.
- Hook up flush for writers and output streams.
- Hook up planck.shell/sh :in for smallish strings.
- Support for `:fn-invoke-direct` compiler option.
- Support `cljs.core/*command-line-args*`.
- Support for `:checked-arrays`.
- Make `*print-fn*` and `*print-err-fn*` like noderepljs for JavaScript objects.
- Pretty print JavaScript arrays and objects.

### Changed
- ClojureScript 1.9.854.
- Update transit-clj to 0.8.300.
- Update fipp to 0.6.8.
- Revise dump SDK option to be `-S`.

### Fixed
- Fixes crashes when using HTTP.
- Don't print `doc`s for macros that haven't been referred.
- Auto-complete after arrow and other special chars.
- Don’t demunge Planck native fns.
- Suppress additional meta printing for unknown types.
- Do a distinct in apropros to provide one response for fn-macros.

## [2.5.0] - 2017-05-26
### Changed
- ClojureScript 1.9.562.
- Pretty-print Eductions.

### Fixed
- Fix crash when making HTTP post.

## [2.4.0] - 2017-05-12
### Changed
- ClojureScript 1.9.542.
- Improve completions.

### Fixed
- Fix crash when making HTTP request.
- Fix find-doc implementation to be more accurate.
- Properly cache internal `run_timeout_fn`.

## [2.3.0] - 2017-04-18
### Added
- Show version with `-V` / `--version`.
- Ability to `-D` / `--dump-sdk`.
- SDK docs on website.
- Imitation of clojure.java.io/resource.
- Add get-arglists function (emacs integration).
- Ability to instrument bundled speced fns.

### Changed
- ClojureScript 1.9.521
- Don’t bundle cljs/core$macros.cljc or the bundle namespace.
- Make path-separator private.
- Make planck.io/build-uri private.
- Load source maps for more bundled namespaces.

### Removed
- Don't bundle `tailrecursion/cljson`.

### Fixed
- Crash if specify dumb as theme.
- Ctrl-C leads to a space after prompt.

## [2.2.0] - 2017-02-25
### Added
- Support for namespaced maps.

### Changed
- ClojureScript 1.9.494
- Deumunge $macros in symbols in macroexpand.

### Fixed
- pprint wrapping honor width.
- Better support for pasting into REPL.
- Handle comments entered in REPL.

## [2.1.0] - 2017-02-10
### Added
- Ability to use Ctrl-R to `reverse-i-search` in the REPL.
- Print `ex-data` in `pst`.
- Better error reporting for malformed scripts.
- Available via `apt-get` on Ubuntu.

### Changed
- ClojureScript 1.9.473.
- Use `poll` instead of `select` in order to async more than 1024 shell processes.

### Fixed
- Add `::body` to spec of `planck.http/post`.

## [2.0.0] - 2017-02-04
### Added
- Support for Linux.
- Async Shell.
- Hi-Res Timer.
- Interrupt Forms Generating Output.
- Source for REPL-defined forms.
- Planck Classpath Environment Variable.

### Changed
- ClojureScript 1.9.456.

## [2.0.0-rc.1] - 2017-01-27
### Changed
- ClojureScript 1.9.456.

### Fixed
- Keep script running until `sh-async` completes.

## [2.0.0-beta.6] - 2017-01-21
### Added
- `source` works for `def` forms evaluated in the REPL.

### Changed
- ClojureScript 1.9.397
- Shut down JavaScriptCore when Planck is shut down.

### Fixed
- Fix a leak.
- Fix bugs related to caching native function references.
- Fix bugs related to `JSValueRef` lifetimes.
- Fix bug when Ctrl-C out of long form.

## [2.0.0-beta.5] - 2017-01-04
### Changed
- ClojureScript 1.9.380.
- Updates to error indicator (caret).
- Optimize `-read-line` perf.
- No longer use source requiring C99 support.
- Improvements waiting for ClojureScript runtime initialization.
- Flow control when writing to Socket REPL.

### Fixed
- Fix a corner case in `BufferedReader` implementation.
- Properly handle bad file descriptors.
- Fix bugs related to secondary prompt display with `-d`.
- Fix a memory leak converting strings.
- Better handling of Socket REPL lifecycle.
- Fix a few buffer overruns.
- Properly route Socket REPL printing when under load.
- Fix crash in `file-seq`.

## [2.0.0-beta.4] - 2016-12-17
### Added
- Can compile with either gcc or clang.
- Use nanosecond-resolution timers for `time` function.
- Use Planck's `eval` when needed by `cljs.spec.test` macros.

### Changed
- ClojureScript 1.9.376.
- Syntax-hightlight specs in `doc` output.
- Various robustness revisions in C source.
- Update the copyright notices (in `-l` output).
- Pre-compile `cljs.spec.test`.

### Fixed
- Don't reload goog JS that has already been loaded.
- Avoid stack overflow with deep require tree.
- Fix the ability to do HTTP POSTs.
- Support `planck.shell/sh` child processes that produce lots of output.

## [2.0.0-beta.3] - 2016-11-27
### Added
- Now builds on NixOS and Centos.
- High resolution timer for `time` macro.
- Added `load` REPL special.

### Changed
- Use the new `require`, `require-macros`, `import`, macros from ClojureScript.

### Fixed
- Fix build on OS X 10.9.5.
- Properly serialize callbacks.
- Fix `sh-async`.
- Guard against missing JARs on classpath causing crash.
- Fix socket REPL indication display (was interleaved with other text).
- Fix endless loop hitting Ctrl-D to stop when using dumb terminal mode.

## [2.0.0-beta.2] - 2016-11-20
### Added
- Ability to install via `brew install --devel planck`.

### Changed
- ClojureScript 1.9.330.
- Add keyword completion candidates `:refer-clojure` `:exclude`.
- Build native portion (C code) with release optimizations.

### Fixed
- Properly initialize `*assert*`.
- Fix issue when cursor has to hop left.
- Fix ability to `(exit 0)`.
- Fix blocking initializing JavaScriptCore on some Unixes.

## [2.0.0-beta.1] - 2016-11-20
### Added
- Support for Linux.
- Support for `PLANCK_CLASSPATH` env var.
- Interruptibility of REPL forms producing output.
- Warn if Planck can’t write to cache path.

### Changed
- ClojureScript 1.9.293.
- Bundle `goog.labs.format.csv`.
- Abort if `-k` and `-K` specified.
- Eliminate double analysis of forms at REPL.
- Update `ns` docstring.

### Fixed
- Preserve `ns-interns` when failing to load a namespace.
- Properly track loaded foreign-libs.
- Fix segfault typing `]` after an error.

## [1.17] - 2016-09-11
### Added
- Report filename for compiler warnings and errors.

### Changed
- ClojureScript 1.9.229.
- Update bundled version of Transit to 0.8.239.
- Update bundled version of Fipp to 0.6.6.

### Fixed
- Close pipes in shell/sh.

## [1.16] - 2016-08-15
### Added
- Support reading namespaced maps.
- Support IOFactory on std streams.

### Changed
- ClojureScript 1.9.216
- Use `org.clojure/tools.reader` 1.0.0-beta3.
- Improvements to tab completions.
- Many revsions for 2.0 alpha C-based port.

### Fixed
- Properly initialize `*assert*` with session state (for `--elide-asserts`).
- Changes to support building on case-sensitive file system.

## [1.15] - 2016-06-18
### Added
- Add indicators for warnings.
- Format macroexpand output as code.
- Spec functions exposed in Planck namespaces.
- Format specs in docstrings.

### Changed
- ClojureScript 1.9.76.

### Fixed
- Wait for timers to complete before exiting (useful for `core.async`).

## [1.14] - 2016-06-16
### Added
- Support for cljs.spec.

### Changed
- ClojureScript 1.9.14.

### Fixed
- Partial fix for `eval` (#288).

## [1.13] - 2016-05-27
### Added
- Column indicators for analysis errors.
- Elide pasted prompts when pasting into REPL.

### Changed
- Isolation for Socket REPLs (`*1`, _etc._).
- Better handling of async printing.
- Add `console.error` (previously only had `console.log`).
- Align secondary prompts for 1-char namespaces.

### Fixed
- Properly handle case when `*print-newline*` is set to `false`.

## [1.12.1] - 2016-05-18
### Fixed
- Fix slurp failing on Mavericks.
- Fix crash with multi-line forms and single-character namespaces.

## [1.12] - 2016-05-15
### Added
- Pretty print REPL results using Fipp with syntax highlighting.
- A new `planck.http` namespace supporting `get` and `post`.
- Support `:foreign-libs` in `deps.cljs` embedded in JARs.
- Support re-configuring control keys used in REPL.
- Support disabling asserts at launch time (like `:elide-asserts`).
- Add simpler cache support via `-K` to create `.planck_cache` dir.

### Changed
- console log simply logs text (w/o NSLog info).
- Don't refer to Maven (brew install w/o Java).
- Use `tools.reader` 1.0.0-beta1.
- Use Parinfer 1.8.1.
- Clean up stack traces (demunge symbols, elide `-invoke`).
- Mark a few vars as private.
- Minimal OS required Mavericks (was Lion).

### Fixed
- Properly align docstring output for vars like `some->`.
- Fix a bug where caches were not invalided when using an updated JAR.

## [1.11] - 2016-04-25
### Added
- A quiet mode that disables banner and other output.
- Support for `clojure.core.reducers`.
- Ability to use `cljs.js` itself within Planck via `planck.core/init-empty-state`.
- Autocompletion on commonly used keywords and other autocomplete improvements.

### Changed
- Update to ClojureScript 1.8.51.
- Use CloureScript `cljs.test` (instead of previous port).

### Fixed
- Fix for multi-arg file, add File/toString.
- Exit with failure when port is bound.
- Fix for caching top-level files.
- Don't inadvertently load bundled artifacts from classpath.

## [1.10] - 2016-03-02
### Added
- Bundle port of `cljs.test` for use in bootstrap ([post](http://blog.fikesfarm.com/posts/2016-02-27-testing-with-planck.html)).
- Colors (with light and dark theme) ([post](http://blog.fikesfarm.com/posts/2016-02-04-planck-colors.html)).
- Indentation (via Parinfer) ([post](http://blog.fikesfarm.com/posts/2016-02-10-indenting-with-parinfer.html)).
- `eval` and friends `resolve`, `ns-resolve`, `intern` ([post](http://blog.fikesfarm.com/posts/2016-01-22-clojurescript-eval.html)).
- Securely prompt for and read password.
- Source mapping for user code.
- `with-open` and `line-seq`.
- `with-sh-dir` and `with-sh-env`.
- `sh` accepts string and file.
- Honor `:forms` in `doc` output.
- `planck.io/file-attributes` (like `fstat`).
- `find-doc`.
- Startup banner with helpful instructions.

### Changed
- Use ClojureScript master (1.8.28).
- Throw exception on error from `sh`.
- `doc` for `catch`, `finally`, `&`.
- AOT compile Planck macro namespaces ([post](http://blog.fikesfarm.com/posts/2016-02-03-planck-macros-aot.html)).

### Fixed
- Throw if using closed file.
- Properly index open JARs (needed for large classpaths).
- You can now `require` `planck.repl` namespace.
- Don't let `cljs.env` be inadvertently reloaded (clears `*compiler*`).
- Search all loaded macro namespaces for docs.

## [1.9] - 2016-01-20
### Added
- New website with comprehensive User Guide: [https://planck-repl.org](http://planck-repl.org).
- Socket REPL.
- Lazily load core analysis caches (2x faster in some cases).
- `apropos` and `dir` support.
- Protocol method doc output.
- Doc support for namespaces.
- Bundle `clojure.zip`, `clojure.data`.
- Multiple forms on a single REPL line.

### Changed
- Use ClojureScript 1.7.228.
- Eliminate noisy var code when in verbose mode.
- Don't use default classpath of `.`.
- Add = signs in `-h` output for long args.
- Mention namespace for `*command-line-args*` in `-h`.
- Improve exception printing.

### Fixed
- Fix crash if command line arg missing.
- Fix so you can use reader literals.
- Fix `source` failing on VMs.
- Purge analysis cache when reloading (allows reloading namespace with constant).
- Fix extra blank lines that appear when pasting.
- Proper caching of macro namespaces.
- If require fails, restore analysis cache state.
- Fix for syntax quote.
- Honor `:static-fns` for cache invalidation.
- Fix for `load-file`.

## [1.8] - 2015-11-06
### Added
- Tab completion for core macros.
- Support for `:static-fns` (via `-s` or `--static-fns`).
- Analysis / compilation cache (`-k <cache dir>` option).
- `planck.core/*planck-version*`.

### Changed
- Use ClojureScript 1.7.170.
- Can now `(require 'cljs.analyzer)`.

### Fixed
- `0` is truthy.
- `doc` fix when using namespace alias.
- `source` fix when using namespace alias.

## [1.7] - 2015-10-03
### Added
- Support OS X 10.11 El Capitan.
- Support OS X 10.7 Lion.
- Support for setTimeout.
- Errors emitted when `spit` and `slurp` fail.
- Support terminating REPL by typing `quit` or `exit`.

### Changed
- Use ClojureScript 1.7.122.
- Experimental analysis / compilation cache (`-k <cache dir>` option).

### Fixed
- `source` REPL special for `planck` namespace code.
- Fall back to ASCII encoding if needed when using `planck.shell`.
- Eliminate residual processing of `b` option.
- Properly merge requires (honoring established namespace aliases).
- Proper VT100 escape sequences (brace matching in iTerm).

## [1.6] - 2015-08-22
### Added
- Support JAR deps.
- Support `-c` / `--classpath` for specifying source directories and JAR deps.
- Support for OS X 10.8 Mountain Lion.
- Ship with bundled `cljs.pprint` and `cljs.test`.
- Support `source` REPL special.
- Support `import` REPL special.
- Support for `planck.core/*command-line-args*`.
- Support `:encoding` option for file I/O.
- Support for byte-oriented streams (`planck.io/input-stream` and `planck.io/output-stream`).
- `doc` output for macros.
- `doc` and tab completion for special forms.

### Changed
- Read pre-compiled namespaces and analysis metadata for faster loading.
- Deprecate `-s` / `--src` in favor of using `-c` / `--classpath`.
- Revise `planck.core/file-seq` to be lazy (in terms of `tree-seq`).

### Fixed
- Capture stderr for `planck.shell/sh`.
- Fix a glitch in brace highlighting when vertically aligned.
- Properly exit if `planck.core/exit` is called with `0`.
- Don't block on I/O in `planck.core/shell/sh`.
- Properly decode UTF-8 from stdin.

## [1.5] - 2015-08-15
### Added
- Brace highlighting (cursor temporarily jumps to previous matching brace).
- Improved `require` & `require-macros` (`:as` _etc._, error reporting).
- `file-seq` support
- `load-file` REPL special.
- Exit codes (`1` if unhandled exception, or explcit via `planck.core/exit`).
- Hit Ctrl-C to get out of a form and get a new prompt.
- Doc output improvement.
- Google Closure indexed so you can require things from there.
- Ship with `cljs.test` / `cljs.pprint` and lots of Google Closure.
- `-d` / `--dumb-terminal` option (facilitates `rlwrap`).
- `-l` / `--legal` to show licenses / copyrights.

### Changed
- Rearranged so that there is now a `planck.core` to hold core fns.
- Smaller binary (gzipped ClojureScript runtime internally).

### Fixed
- Don't lock up on syntax error.

## [1.4] - 2015-08-09
### Added
- 2× launch perf improvement (for scripts).
- Execute host commands via `planck.shell` namespace.
- Streaming file I/O: `reader`/`writer` using `IOFactory`.
- Repeated ordered `-e` `-i` options.
- Improved tab completion (considers namespaces, specials, _etc_.).
- `pst` (print stack trace) support.
- Mavericks support.

### Changed
- ClojureScript 1.7.28 -> 1.7.48.
- Work towards improved `doc` support.

### Fixed
- Crash with single-char ns.
- Printing to `stderr` via `*print-err-fn*`.
- Properly suppress printing of `nil` values for launch opts.
- Fixes some crashes mishandling UTF-8.

## [1.3] - 2015-08-03
### Changed
- Updated to no longer depend on CocoaPods.
- Fast startup even with readline support.
- Tweaks for tab completion (macros included, private vars excluded).
- Cleanup startup options, especially with respect to those used for dev.

## [1.2] - 2015-08-02
### Added
- Source mapping of stack traces.
- Readline support and auto-completion.

## [1.1] - 2015-08-01
### Added
- Adds `read-line` support.

### Fixed
- Fixes ^D so that it exits REPL.

## [1.0] - 2015-07-31
### Added
- Initial release.

[Unreleased]: https://github.com/mfikes/planck/compare/2.23.0...HEAD
[2.23.0]: https://github.com/mfikes/planck/compare/2.22.0...2.23.0
[2.22.0]: https://github.com/mfikes/planck/compare/2.21.0...2.22.0
[2.21.0]: https://github.com/mfikes/planck/compare/2.20.0...2.21.0
[2.20.0]: https://github.com/mfikes/planck/compare/2.19.1...2.20.0
[2.19.0]: https://github.com/mfikes/planck/compare/2.18.1...2.19.0
[2.18.1]: https://github.com/mfikes/planck/compare/2.18.0...2.18.1
[2.18.0]: https://github.com/mfikes/planck/compare/2.17.0...2.18.0
[2.17.0]: https://github.com/mfikes/planck/compare/2.16.0...2.17.0
[2.16.0]: https://github.com/mfikes/planck/compare/2.15.0...2.16.0
[2.15.0]: https://github.com/mfikes/planck/compare/2.14.0...2.15.0
[2.14.0]: https://github.com/mfikes/planck/compare/2.13.0...2.14.0
[2.13.0]: https://github.com/mfikes/planck/compare/2.12.6...2.13.0
[2.12.6]: https://github.com/mfikes/planck/compare/2.12.0...2.12.6
[2.12.0]: https://github.com/mfikes/planck/compare/2.11.0...2.12.0
[2.11.0]: https://github.com/mfikes/planck/compare/2.10.0...2.11.0
[2.10.0]: https://github.com/mfikes/planck/compare/2.9.0...2.10.0
[2.9.0]: https://github.com/mfikes/planck/compare/2.8.1...2.9.0
[2.8.1]: https://github.com/mfikes/planck/compare/2.8.0...2.8.1
[2.8.0]: https://github.com/mfikes/planck/compare/2.7.3...2.8.0
[2.7.3]: https://github.com/mfikes/planck/compare/2.7.0...2.7.3
[2.7.0]: https://github.com/mfikes/planck/compare/2.6.0...2.7.0
[2.6.0]: https://github.com/mfikes/planck/compare/2.5.0...2.6.0
[2.5.0]: https://github.com/mfikes/planck/compare/2.4.0...2.5.0
[2.4.0]: https://github.com/mfikes/planck/compare/2.3.0...2.4.0
[2.3.0]: https://github.com/mfikes/planck/compare/2.2.0...2.3.0
[2.2.0]: https://github.com/mfikes/planck/compare/2.1.0...2.2.0
[2.1.0]: https://github.com/mfikes/planck/compare/2.0.0...2.1.0
[2.0.0]: https://github.com/mfikes/planck/compare/2.0.0-rc.1...2.0.0
[2.0.0-rc.1]: https://github.com/mfikes/planck/compare/2.0.0-beta.6...2.0.0-rc.1
[2.0.0-beta.6]: https://github.com/mfikes/planck/compare/2.0.0-beta.5...2.0.0-beta.6
[2.0.0-beta.5]: https://github.com/mfikes/planck/compare/2.0.0-beta.4...2.0.0-beta.5
[2.0.0-beta.4]: https://github.com/mfikes/planck/compare/2.0.0-beta.3...2.0.0-beta.4
[2.0.0-beta.3]: https://github.com/mfikes/planck/compare/2.0.0-beta.2...2.0.0-beta.3
[2.0.0-beta.2]: https://github.com/mfikes/planck/compare/2.0.0-beta.1...2.0.0-beta.2
[2.0.0-beta.1]: https://github.com/mfikes/planck/compare/1.17...2.0.0-beta.1
[1.17]: https://github.com/mfikes/planck/compare/1.16...1.17
[1.16]: https://github.com/mfikes/planck/compare/1.15...1.16
[1.15]: https://github.com/mfikes/planck/compare/1.14...1.15
[1.14]: https://github.com/mfikes/planck/compare/1.13...1.14
[1.13]: https://github.com/mfikes/planck/compare/1.12.1...1.13
[1.12.1]: https://github.com/mfikes/planck/compare/1.12...1.12.1
[1.12]: https://github.com/mfikes/planck/compare/1.11...1.12
[1.11]: https://github.com/mfikes/planck/compare/1.10...1.11
[1.10]: https://github.com/mfikes/planck/compare/1.9...1.10
[1.9]: https://github.com/mfikes/planck/compare/1.8...1.9
[1.8]: https://github.com/mfikes/planck/compare/1.7...1.8
[1.7]: https://github.com/mfikes/planck/compare/1.6...1.7
[1.6]: https://github.com/mfikes/planck/compare/1.5...1.6
[1.5]: https://github.com/mfikes/planck/compare/1.4...1.5
[1.4]: https://github.com/mfikes/planck/compare/1.3...1.4
[1.3]: https://github.com/mfikes/planck/compare/1.2...1.3
[1.2]: https://github.com/mfikes/planck/compare/1.1...1.2
[1.1]: https://github.com/mfikes/planck/compare/1.0...1.1
[1.0]: https://github.com/mfikes/planck/compare/63d50fe673c147e2d5810424daa75344f36b33bb...1.0
