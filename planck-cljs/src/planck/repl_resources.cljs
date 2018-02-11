(ns planck.repl-resources
  "Resources for use in the Planck REPL implementation.")

(def special-doc-map
  '{.     {:forms [(.instanceMethod instance args*)
                   (.-instanceField instance)]
           :doc   "The instance member form works for methods and fields.
  They all expand into calls to the dot operator at macroexpansion time."}
    ns    {:forms [(name docstring? attr-map? references*)]
           :doc   "You must currently use the ns form only with the following caveats

    * You must use the :only form of :use
    * :require supports :as, :refer, and :rename
      - all options can be skipped
      - in this case a symbol can be used as a libspec directly
        - that is, (:require lib.foo) and (:require [lib.foo]) are both
          supported and mean the same thing
      - :rename specifies a map from referred var names to different
        symbols (and can be used to prevent clashes)
      - prefix lists are not supported
    * The only options for :refer-clojure are :exclude and :rename
    * :import is available for importing Google Closure classes
      - ClojureScript types and records should be brought in with :use
        or :require :refer, not :import ed
    * Macros must be defined in a different compilation stage than the one
      from where they are consumed. One way to achieve this is to define
      them in one namespace and use them from another. They are referenced
      via the :require-macros / :use-macros options to ns
      - :require-macros and :use-macros support the same forms that
        :require and :use do

  Implicit macro loading: If a namespace is required or used, and that
  namespace itself requires or uses macros from its own namespace, then
  the macros will be implicitly required or used using the same
  specifications. Furthermore, in this case, macro vars may be included
  in a :refer or :only spec. This oftentimes leads to simplified library
  usage, such that the consuming namespace need not be concerned about
  explicitly distinguishing between whether certain vars are functions
  or macros. For example:

  (ns testme.core (:require [cljs.test :as test :refer [test-var deftest]]))

  will result in test/is resolving properly, along with the test-var
  function and the deftest macro being available unqualified.

  Inline macro specification: As a convenience, :require can be given
  either :include-macros true or :refer-macros [syms...]. Both desugar
  into forms which explicitly load the matching Clojure file containing
  macros. (This works independently of whether the namespace being
  required internally requires or uses its own macros.) For example:

  (ns testme.core
  (:require [foo.core :as foo :refer [foo-fn] :include-macros true]
            [woz.core :as woz :refer [woz-fn] :refer-macros [app jx]]))

  is sugar for

  (ns testme.core
  (:require [foo.core :as foo :refer [foo-fn]]
            [woz.core :as woz :refer [woz-fn]])
  (:require-macros [foo.core :as foo]
                   [woz.core :as woz :refer [app jx]]))

  Auto-aliasing clojure namespaces: If a non-existing clojure.* namespace
  is required or used and a matching cljs.* namespace exists, the cljs.*
  namespace will be loaded and an alias will be automatically established
  from the clojure.* namespace to the cljs.* namespace. For example:

  (ns testme.core (:require [clojure.test]))

  will be automatically converted to

  (ns testme.core (:require [cljs.test :as clojure.test]))"}
    def   {:forms [(def symbol doc-string? init?)]
           :doc   "Creates and interns a global var with the name
  of symbol in the current namespace (*ns*) or locates such a var if
  it already exists.  If init is supplied, it is evaluated, and the
  root binding of the var is set to the resulting value.  If init is
  not supplied, the root binding of the var is unaffected."}
    do    {:forms [(do exprs*)]
           :doc   "Evaluates the expressions in order and returns the value of
  the last. If no expressions are supplied, returns nil."}
    if    {:forms [(if test then else?)]
           :doc   "Evaluates test. If not the singular values nil or false,
  evaluates and yields then, otherwise, evaluates and yields else. If
  else is not supplied it defaults to nil."}
    new   {:forms [(Constructor. args*) (new Constructor args*)]
           :url   "java_interop#new"
           :doc   "The args, if any, are evaluated from left to right, and
  passed to the JavaScript constructor. The constructed object is
  returned."}
    quote {:forms [(quote form)]
           :doc   "Yields the unevaluated form."}
    recur {:forms [(recur exprs*)]
           :doc   "Evaluates the exprs in order, then, in parallel, rebinds
  the bindings of the recursion point to the values of the exprs.
  Execution then jumps back to the recursion point, a loop or fn method."}
    set!  {:forms [(set! var-symbol expr)
                   (set! (.- instance-expr instanceFieldName-symbol) expr)]
           :url   "vars#set"
           :doc   "Used to set vars and JavaScript object fields"}
    throw {:forms [(throw expr)]
           :doc   "The expr is evaluated and thrown."}
    try   {:forms [(try expr* catch-clause* finally-clause?)]
           :doc   "catch-clause => (catch classname name expr*)
  finally-clause => (finally expr*)
  Catches and handles JavaScript exceptions."}
    var   {:forms [(var symbol)]
           :doc   "The symbol must resolve to a var, and the Var object
itself (not its value) is returned. The reader macro #'x expands to (var x)."}})

(def repl-special-doc-map
  '{in-ns     {:arglists ([name])
               :doc      "Sets *cljs-ns* to the namespace named by the symbol, creating it if needed."}
    load-file {:arglists ([name])
               :doc      "Sequentially read and evaluate the set of forms contained in the file."}
    load      {:arglists ([& paths])
               :doc      "Loads Clojure code from resources in classpath. A path is interpreted as
  classpath-relative if it begins with a slash or relative to the root
  directory for the current namespace otherwise."}})

(def bundled-aot-namespaces
  '[cljs.core
    cljs.analyzer.api
    cljs.compiler
    cljs.env
    cljs.js
    cljs.pprint
    cljs.reader
    cljs.source-map
    cljs.source-map.base64
    cljs.source-map.base64-vlq
    cljs.spec.alpha
    cljs.spec.gen.alpha
    cljs.spec.test.alpha
    cljs.stacktrace
    cljs.tagged-literals
    cljs.test
    cljs.tools.reader
    cljs.tools.reader.impl.commons
    cljs.tools.reader.impl.utils
    cljs.tools.reader.reader-types
    clojure.core.reducers
    clojure.core.rrb-vector
    clojure.core.rrb-vector.interop
    clojure.core.rrb-vector.nodes
    clojure.core.rrb-vector.protocols
    clojure.core.rbb-vector.rbbt
    clojure.core.rbb-vector.transients
    clojure.core.rrb-vector.trees
    clojure.data
    clojure.set
    clojure.string
    clojure.walk
    clojure.zip
    cognitect.transit
    fipp.clojure
    fipp.deque
    fipp.edn
    fipp.ednize
    fipp.visit
    lazy-map.core
    planck.core
    planck.from.io.aviso.ansi
    planck.http
    planck.io
    planck.js-deps
    planck.pprint.code
    planck.pprint.data
    planck.pprint.width-adjust
    planck.repl
    planck.repl-resources
    planck.shell
    planck.themes
    goog.array.array
    goog.asserts.asserts
    goog.async.delay
    goog.async.freelist
    goog.async.nexttick
    goog.async.run
    goog.async.throttle
    goog.async.workqueue
    goog.base
    goog.color.alpha
    goog.color.color
    goog.color.names
    goog.crypt.aes
    goog.crypt.arc4
    goog.crypt.base64
    goog.crypt.basen
    goog.crypt.blobhasher
    goog.crypt.blockcipher
    goog.crypt.cbc
    goog.crypt.crypt
    goog.crypt.hash
    goog.crypt.hash32
    goog.crypt.hashtester
    goog.crypt.hmac
    goog.crypt.md5
    goog.crypt.pbkdf2
    goog.crypt.sha1
    goog.crypt.sha2
    goog.crypt.sha224
    goog.crypt.sha256
    goog.crypt.sha2_64bit
    goog.crypt.sha512
    goog.crypt.sha512_256
    goog.date.date
    goog.date.datelike
    goog.date.daterange
    goog.date.duration
    goog.date.relative
    goog.date.relativewithplurals
    goog.date.utcdatetime
    goog.debug.console
    goog.debug.debug
    goog.debug.entrypointregistry
    goog.debug.error
    goog.debug.errorcontext
    goog.debug.formatter
    goog.debug.logbuffer
    goog.debug.logger
    goog.debug.logrecord
    goog.debug.relativetimeprovider
    goog.disposable.disposable
    goog.disposable.idisposable
    goog.dom.asserts
    goog.dom.browserfeature
    goog.dom.dom
    goog.dom.htmlelement
    goog.dom.nodetype
    goog.dom.safe
    goog.dom.tagname
    goog.dom.tags
    goog.events.browserevent
    goog.events.browserfeature
    goog.events.event
    goog.events.eventid
    goog.events.events
    goog.events.eventtarget
    goog.events.eventtype
    goog.events.listenable
    goog.events.listener
    goog.events.listenermap
    goog.format.emailaddress
    goog.format.format
    goog.format.htmlprettyprinter
    goog.format.internationalizedemailaddress
    goog.format.jsonprettyprinter
    goog.fs.entry
    goog.fs.entryimpl
    goog.fs.error
    goog.fs.filereader
    goog.fs.filesaver
    goog.fs.filesystem
    goog.fs.filesystemimpl
    goog.fs.filewriter
    goog.fs.fs
    goog.fs.progressevent
    goog.fs.url
    goog.functions.functions
    goog.html.safehtml
    goog.html.safescript
    goog.html.safestyle
    goog.html.safestylesheet
    goog.html.safeurl
    goog.html.trustedresourceurl
    goog.html.uncheckedconversions
    goog.i18n.bidi
    goog.i18n.bidiformatter
    goog.i18n.charlistdecompressor
    goog.i18n.charpickerdata
    goog.i18n.collation
    goog.i18n.compactnumberformatsymbols
    goog.i18n.currency
    goog.i18n.datetimeformat
    goog.i18n.datetimeparse
    goog.i18n.datetimepatterns
    goog.i18n.datetimesymbols
    goog.i18n.graphemebreak
    goog.i18n.messageformat
    goog.i18n.mime
    goog.i18n.numberformat
    goog.i18n.numberformatsymbols
    goog.i18n.ordinalrules
    goog.i18n.pluralrules
    goog.i18n.timezone
    goog.i18n.uchar.localnamefetcher
    goog.i18n.uchar.namefetcher
    goog.i18n.uchar.remotenamefetcher
    goog.i18n.uchar
    goog.i18n.ucharnames
    goog.iter.iter
    goog.json.evaljsonprocessor
    goog.json.hybrid
    goog.json.json
    goog.json.nativejsonprocessor
    goog.json.processor
    goog.labs.useragent.browser
    goog.labs.useragent.engine
    goog.labs.useragent.platform
    goog.labs.useragent.util
    goog.locale.defaultlocalenameconstants
    goog.locale.genericfontnames
    goog.locale.locale
    goog.locale.nativenameconstants
    goog.locale.timezonedetection
    goog.locale.timezonefingerprint
    goog.log.log
    goog.loremipsum.text.loremipsum
    goog.math.affinetransform
    goog.math.bezier
    goog.math.box
    goog.math.coordinate
    goog.math.coordinate3
    goog.math.exponentialbackoff
    goog.math.integer
    goog.math.interpolator.interpolator1
    goog.math.interpolator.linear1
    goog.math.interpolator.pchip1
    goog.math.interpolator.spline1
    goog.math.irect
    goog.math.line
    goog.math.long
    goog.math.math
    goog.math.matrix
    goog.math.path
    goog.math.paths
    goog.math.range
    goog.math.rangeset
    goog.math.rect
    goog.math.size
    goog.math.tdma
    goog.math.vec2
    goog.math.vec3
    goog.mochikit.async.deferred
    goog.net.errorcode
    goog.net.eventtype
    goog.net.httpstatus
    goog.net.wrapperxmlhttpfactory
    goog.net.xhrio
    goog.net.xhrlike
    goog.net.xmlhttp
    goog.net.xmlhttpfactory
    goog.object.object
    goog.promise.promise
    goog.promise.resolver
    goog.promise.thenable
    goog.reflect.reflect
    goog.spell.spellcheck
    goog.string.const
    goog.string.newlines
    goog.string.parser
    goog.string.string
    goog.string.stringbuffer
    goog.string.stringformat
    goog.string.stringifier
    goog.string.typedstring
    goog.structs.avltree
    goog.structs.circularbuffer
    goog.structs.collection
    goog.structs.heap
    goog.structs.inversionmap
    goog.structs.linkedmap
    goog.structs.map
    goog.structs.node
    goog.structs.pool
    goog.structs.prioritypool
    goog.structs.priorityqueue
    goog.structs.quadtree
    goog.structs.queue
    goog.structs.set
    goog.structs.simplepool
    goog.structs.stringset
    goog.structs.structs
    goog.structs.treenode
    goog.structs.trie
    goog.testing.asserts
    goog.testing.jsunitexception
    goog.testing.performancetable
    goog.testing.pseudorandom
    goog.testing.stacktrace
    goog.timer.timer
    goog.uri.uri
    goog.uri.utils
    goog.useragent.product
    goog.useragent.useragent])
