(ns planck.http-test
  (:require [clojure.test :refer [deftest testing is]]
            [cognitect.transit :as transit]
            [planck.http]))

;; A server running an instance of https://github.com/mfikes/http-echo-clj
(def http-echo-server "http://http-test.planck-repl.org")

(deftest request-test
  (testing "request"
    (is (every? (planck.http/request identity :get "url" {}) [:url :method :headers :timeout]))
    (is (= planck.http/default-timeout
          (:timeout (planck.http/request identity :get "url" {}))))
    (is (= "GET" (:method (planck.http/request identity :get "url" {}))))
    (is (= "url" (:url (planck.http/request identity :get "url" {}))))
    (is (= 30 (:timeout (planck.http/request identity :get "url" {:timeout 30}))))
    (is (= "foo=bar" (:body (planck.http/request identity :get "url"
                              {:form-params {:foo "bar"}}))))
    (is (= (count "foo=bar") (-> (planck.http/request identity :get "url"
                                   {:form-params {:foo "bar"}})
                               (get-in [:headers :Content-length]))))
    (let [params [["foo" "bar"]]
          expected-body (planck.http/generate-multipart-body
                          (planck.http/boundary planck.http/boundary-constant)
                          (planck.http/generate-form-data params))]
      (is (= (count expected-body) (-> (planck.http/request identity :get "url"
                                         {:multipart-params params})
                                     :body
                                     count)))
      (is (= (count expected-body) (-> (planck.http/request identity :get "url"
                                         {:multipart-params params})
                                     (get-in [:headers :Content-length])))))))

(deftest generate-query-string-test
  (testing "generate-query-string"
    (is (= "foo=bar" (planck.http/generate-query-string {:foo "bar"})))
    (let [q (planck.http/generate-query-string {:foo "bar" :bar "baz"})]
      (is (or (= q "bar=baz&foo=bar") (= q "foo=bar&bar=baz")))) ;; same difference
    (is (= "foo=b%3Dar" (planck.http/generate-query-string {:foo "b=ar"})))))

(deftest wrap-content-type-test
  (testing "wrap-content-type"
    (is (= "application/json" (-> {:content-type :json :headers {}}
                                ((planck.http/wrap-content-type identity))
                                (get-in [:headers "Content-Type"]))))
    (is (= "application/xml" (-> {:content-type :xml}
                               ((planck.http/wrap-content-type identity))
                               (get-in [:headers "Content-Type"]))))
    (is (= "application/x-www-form-urlencoded" (-> {:content-type :form-urlencoded}
                                                 ((planck.http/wrap-content-type identity))
                                                 (get-in [:headers "Content-Type"]))))
    (is (= "ugle" (-> {:content-type "ugle"}
                    ((planck.http/wrap-content-type identity))
                    (get-in [:headers "Content-Type"]))))
    (is (= nil (-> {:content-type "ugle"}
                 ((planck.http/wrap-content-type identity))
                 :content-type)))
    (is (= nil (-> {}
                 ((planck.http/wrap-content-type identity))
                 (get-in [:headers "Content-Type"]))))))

(deftest wrap-accepts-test
  (testing "wrap-accept"
    (is (= "application/json" (-> {:accept :json}
                                ((planck.http/wrap-accepts identity))
                                (get-in [:headers "Accept"]))))
    (is (= nil (-> {:accept :json}
                 ((planck.http/wrap-accepts identity))
                 :accept)))))

(deftest wrap-content-length-test
  (testing "wrap-content-length"
    (is (= (count "uglepose") (-> {:body "uglepose"}
                                ((planck.http/wrap-add-content-length identity))
                                (get-in [:headers "Content-length"]))))))

(deftest wrap-form-params-test
  (testing "wrap-form-params"
    (is (= nil (-> {:form-params "foo"}
                 ((planck.http/wrap-form-params identity))
                 :form-params)))
    (is (= "foo=bar" (-> {:form-params {:foo "bar"}}
                       ((planck.http/wrap-form-params identity))
                       :body)))))

(deftest generate-form-data-test
  (testing "fileupload-stuff"
    (let [expected (str planck.http/content-disposition "foo\"\n\nbar")]
      (is (= [expected "--\n"]
            (planck.http/generate-form-data [["foo" "bar"]]))))
    (is (= [(str planck.http/content-disposition "foo\"; filename=\"bar\"\n"
              "Content-Type: application/octet-stream\n\n" "baz") "--\n"]
          (planck.http/generate-form-data [["foo" ["baz" "bar"]]])))))

(deftest generate-multipart-body-test
  (testing "generate-multipart-body"
    (is (= "boundarypart1\nboundarypart2"
          (planck.http/generate-multipart-body "boundary" ["part1" "part2"])))))

(deftest boundary-test
  (testing "boundary"
    (is (not= nil (re-matches #"u\d{10}" (planck.http/boundary "u"))))
    (is (not= (planck.http/boundary "u") (planck.http/boundary "u")))))

(deftest wrap-multipart-params-test
  (testing "wrap-multipart-params"
    (is (nil? (-> {:multipart-params [["foo" "bar"]]}
                ((planck.http/wrap-multipart-params identity))
                :multipart-params)))
    (is ((not nil?) (-> {:multipart-params [["foo" "bar"]]}
                      ((planck.http/wrap-multipart-params identity))
                      :body)))
    (is ((not nil?) (-> {:multipart-params [["foo" "bar"]]}
                      ((planck.http/wrap-multipart-params identity))
                      :content-type)))))

(deftest wrap-throw-on-error-test
  (testing "wrap-throw-on-error"
    (is (= "Error: foo" (try (-> {:error "foo"}
                               ((planck.http/wrap-throw-on-error identity)))
                             (catch js/Object e
                               (.toString e)))))))

(defn transit->clj
  [s]
  (let [reader (transit/reader :json)]
    (transit/read reader s)))

(defn form-full-url
  [url-suffix]
  (str http-echo-server url-suffix))

(defn do-get
  ([url-suffix] (do-get url-suffix {}))
  ([url-suffix opts] (->> (planck.http/get (form-full-url url-suffix) opts)
                :body
                transit->clj)))

(deftest http-get-uri-test
  (is (= "/" (:uri (do-get "/"))))
  (is (= "/foo" (:uri (do-get "/foo")))))