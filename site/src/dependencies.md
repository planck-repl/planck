## Dependencies

Planck can depend on other libraries. To do so, the library must be available on an accessible filesystem, either as a source tree or bundled in a JAR, and included in Planck's classpath. You specify the classpath for Planck by providing a colon-separated list of directories and/or JARs via the `-c` or `--classpath` argument.

For example,

```sh
planck -c src:/path/to/foo.jar:some-lib/src
```
will cause Planck to search in the `src` directory first, and then in `foo.jar` next, and finally `some-lib/src` for files when processing `require`, `require-macros`, and `import` direcives (either in the REPL, or as part of `ns` forms.)

Note that, since Planck employs bootstrapped ClojureScript, not all regular ClojureScript libraries may work with Planck. In particular, libraries that employ macros that rely on Java interop cannot work. But libraries that employ straightworward macros that expand to ClojureScript work fine.

One example of Planck using a dependency: This documentation is written in markdown, but converted to HTML using Planck using Dmitri Sotnikov's  [markdown-clj](https://github.com/yogthos/markdown-clj) library. This library is written with support for regular ClojureScript, but it also works perfectly well in bootstrapped ClojureScript.
