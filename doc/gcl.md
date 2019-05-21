## Google Closure Library

Planck bundles the majority of the [Google Closure library](https://developers.google.com/closure/library/). The following namespaces are not included:

* goog.debug
* goog.demos
* goog.editor
* goog.events
* goog.fx
* goog.graphics
* goog.labs
* goog.net.testdata
* goog.testing
* goog.ui

These namespaces can be included in custom builds of Planck by editing the directories that are set to be ignored in the `script/get-closure-library` file. Planck will then need to be [built from source](https://cljdoc.org/d/planck/planck/doc/setup).
