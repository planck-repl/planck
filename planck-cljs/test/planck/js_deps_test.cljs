(ns planck.js-deps-test
  (:require [cljs.test :refer-macros [deftest is]]
            [planck.js-deps :as js-deps]))

(deftest add-foreign-libs-test
  (let [fl {:foreign-libs
                     [{:file     "jquery/jquery.js"
                       :file-min "jquery/jquery.min.js"
                       :provides ["org.jquery.jQuery"]}
                      {:file     "jquery/ui/core.js"
                       :file-min "jquery/ui/core.min.js"
                       :provides ["org.jquery.ui.Core"]
                       :requires ["org.jquery.jQuery"]}
                      {:file     "jquery/ui/autocomplete.js"
                       :file-min "jquery/ui/autocomplete.min.js"
                       :provides ["org.jquery.ui.Autocomplete"]
                       :requires ["org.jquery.ui.Core"]}]
            :externs ["jquery/jquery.js" "jquery/jquery.ui.js"]}]
    (is (= {'org.jquery.jQuery          {:file     "jquery/jquery.js"
                                         :file-min "jquery/jquery.min.js"
                                         :provides ["org.jquery.jQuery"]}
            'org.jquery.ui.Core         {:file     "jquery/ui/core.js"
                                         :file-min "jquery/ui/core.min.js"
                                         :provides ["org.jquery.ui.Core"]
                                         :requires ["org.jquery.jQuery"]}
            'org.jquery.ui.Autocomplete {:file     "jquery/ui/autocomplete.js"
                                         :file-min "jquery/ui/autocomplete.min.js"
                                         :provides ["org.jquery.ui.Autocomplete"]
                                         :requires ["org.jquery.ui.Core"]}}
          (js-deps/add-foreign-libs
            {}
            (:foreign-libs fl)))))
  (let [fl {:foreign-libs [{:file     "x"
                            :provides ["alpha.beta"]}]}]
    (is (= {'already.there {:file     "y"
                            :provides ["already.there"]}
            'alpha.beta    {:file     "x"
                            :provides ["alpha.beta"]}}
          (js-deps/add-foreign-libs
            {'already.there {:file     "y"
                             :provides ["already.there"]}}
            (:foreign-libs fl)))))
  (let [fl {:foreign-libs [{:file     "x"
                            :provides ["a.b" "c.d"]}]}]
    (is (= {'a.b {:file     "x"
                  :provides ["a.b" "c.d"]}
            'c.d {:file     "x"
                  :provides ["a.b" "c.d"]}}
          (js-deps/add-foreign-libs
            {}
            (:foreign-libs fl))))))

(deftest topo-sorted-deps-test
  (let [index {'org.foo.bar.base           {:file     "org/foo/bar/base.js"
                                            :provides ["org.foo.bar.base"]}
               'org.jquery.jQuery          {:file     "jquery/jquery.js"
                                            :file-min "jquery/jquery.min.js"
                                            :provides ["org.jquery.jQuery"]
                                            :requires ["org.foo.bar.base"]}
               'org.jquery.ui.Core         {:file     "jquery/ui/core.js"
                                            :file-min "jquery/ui/core.min.js"
                                            :provides ["org.jquery.ui.Core" "other.foo.Bar"]
                                            :requires ["org.jquery.jQuery" "org.foo.bar.base"]}
               'org.jquery.ui.Autocomplete {:file     "jquery/ui/autocomplete.js"
                                            :file-min "jquery/ui/autocomplete.min.js"
                                            :provides ["org.jquery.ui.Autocomplete"]
                                            :requires ["org.jquery.ui.Core"]}}]
    (is (= '[org.foo.bar.base org.jquery.jQuery org.jquery.ui.Core org.jquery.ui.Autocomplete]
          (js-deps/topo-sorted-deps index 'org.jquery.ui.Autocomplete)))
    (is (= '[org.foo.bar.base org.jquery.jQuery org.jquery.ui.Core]
          (js-deps/topo-sorted-deps index 'org.jquery.ui.Core)))
    (is (= '[org.foo.bar.base org.jquery.jQuery]
          (js-deps/topo-sorted-deps index 'org.jquery.jQuery)))
    (is (= '[org.foo.bar.base]
          (js-deps/topo-sorted-deps index 'org.foo.bar.base)))))

(deftest files-to-load-test
  (let [index {'org.foo.bar.base           {:file     "org/foo/bar/base.js"
                                            :provides ["org.foo.bar.base"]}
               'org.jquery.jQuery          {:file     "jquery/jquery.js"
                                            :file-min "jquery/jquery.min.js"
                                            :provides ["org.jquery.jQuery"]
                                            :requires ["org.foo.bar.base"]}
               'org.jquery.ui.Core         {:file     "jquery/ui/core.js"
                                            :file-min "jquery/ui/core.min.js"
                                            :provides ["org.jquery.ui.Core" "other.foo.Bar"]
                                            :requires ["org.jquery.jQuery" "org.foo.bar.base"]}
               'org.jquery.ui.Autocomplete {:file     "jquery/ui/autocomplete.js"
                                            :file-min "jquery/ui/autocomplete.min.js"
                                            :provides ["org.jquery.ui.Autocomplete"]
                                            :requires ["org.jquery.ui.Core"]}}]
    (is (= ["org/foo/bar/base.js" "jquery/jquery.js" "jquery/ui/core.js" "jquery/ui/autocomplete.js"]
          (js-deps/files-to-load* index 'org.jquery.ui.Autocomplete)))
    (is (= ["org/foo/bar/base.js" "jquery/jquery.js" "jquery/ui/core.js"]
          (js-deps/files-to-load* index 'org.jquery.ui.Core)))
    (is (= ["org/foo/bar/base.js" "jquery/jquery.js"]
          (js-deps/files-to-load* index 'org.jquery.jQuery)))
    (is (= ["org/foo/bar/base.js"]
          (js-deps/files-to-load* index 'org.foo.bar.base)))))
