(ns build.core
  (:require [markdown.core :refer [md->html]]
            [planck.core :refer [slurp spit]]
            [planck.shell :refer [sh]]))

(def src "src/")
(def target "target/")
(def public (str target "public/"))

(def preamble (slurp (str src "preamble.html")))
(def postamble (slurp (str src "postamble.html")))

(defn wrap-html
  [body]
  (str preamble body postamble))

(defn md-to-html
  [in out]
  (spit out
    (wrap-html
      (md->html (slurp in)))))

(defn process-md
  [in]
  (md-to-html
    (str src in) (str public (subs in 0 (- (count in) 2)) "html")))

(sh "mkdir" target)
(sh "rm" "-rf" public)
(sh "mkdir" public)

(process-md "index.md")
(process-md "guide.md")

(def doc-sections ["intro"
                   "setup"
                   "running"
                   "repl"
                   "one-liners"
                   "scripts"
                   "planck-namespaces"
                   "source-dev"
                   "testing"
                   "dependencies"
                   "performance"
                   "socket-repl"
                   "ides"
                   "internals"
                   "contributing"
                   "legal"])

(defn doc-section->md-file
  [doc-section]
  (str doc-section ".md"))

(run! (fn [doc-section]
        (process-md (doc-section->md-file doc-section)))
  doc-sections)

(spit (str public "guide-all.html")
  (wrap-html (apply str
               (map (fn [doc-section]
                      (->> doc-section
                        doc-section->md-file
                        (str src)
                        slurp
                        md->html))
                 doc-sections))))

(sh "cp" "-r" (str src "img") public)
(sh "cp" "-r" (str src "css") public)
(sh "cp" "-r" (str src "js") public)
