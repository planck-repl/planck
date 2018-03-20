(ns planck.http
  "Planck HTTP functionality."
  (:refer-clojure :exclude [get])
  (:require
   [cljs.spec.alpha :as s]
   [clojure.string :as string]
   [planck.repl :as repl]))

(def ^:private content-types {:json            "application/json"
                              :xml             "application/xml"
                              :form-urlencoded "application/x-www-form-urlencoded"})

(def ^:private ^:const default-timeout 5)

(def ^:private ^:const boundary-constant "---------------planck-rocks-")

(def ^:private ^:const content-disposition "\nContent-Disposition: form-data; name=\"")

(defn- encode-val [k v]
  (str (js/encodeURIComponent (name k)) "=" (js/encodeURIComponent (str v))))

(defn- encode-vals [k vs]
  (->>
    vs
    (map #(encode-val k %))
    (string/join "&")))

(defn- encode-param [[k v]]
  (if (coll? v)
    (encode-vals k v)
    (encode-val k v)))

(defn- generate-query-string [params]
  (->>
    params
    (map encode-param)
    (string/join "&")))

(defn- maybe-add-header [request key header-key]
  (when-let [val (key request)]
    (let [header-value (if (keyword? val)
                         (val content-types)
                         val)]
      (merge {header-key header-value} (:headers request)))))

(defn- wrap-content-type
  "Set the appropriate Content Type header."
  [client]
  (fn [request]
    (if-let [headers (maybe-add-header request :content-type "Content-Type")]
      (-> request
        (dissoc :content-type)
        (assoc :headers headers)
        client)
      (client request))))

(defn- wrap-accepts
  "Set the appropriate Accept header."
  [client]
  (fn [request]
    (if-let [headers (maybe-add-header request :accept "Accept")]
      (-> request
        (dissoc :accept)
        (assoc :headers headers)
        client)
      (client request))))

(defn- wrap-debug
  "adds the request to the response if :debug is present"
  [client]
  (fn [request]
    (if-let [debug (:debug request)]
      (let [req (dissoc request :debug)]
        (assoc (client req) :request req))
      (client request))))

(defn- wrap-add-content-length
  "Adds content-length if :body is present "
  [client]
  (fn [request]
    (if-let [body (:body request)]
      (let [headers (merge {"Content-length" (count body)} (:headers request))]
        (-> request
          (assoc :headers headers)
          client))
      (client request))))

(defn- wrap-form-params
  "Adds form-params and content-type"
  [client]
  (fn [request]
    (if-let [form-params (:form-params request)]
      (-> request
        (dissoc :form-params)
        (assoc :content-type :form-urlencoded)
        (assoc :body (generate-query-string form-params))
        client)
      (client request))))

(defn- wrap-add-headers
  "Adds headers to the request if they're not present"
  [client]
  (fn [request]
    (client (assoc request :headers (or (:headers request) {})))))

(defn- wrap-add-timeout
  "Adds default timeout if :timeout is not present"
  [client timeout]
  (fn [request]
    (client (assoc request :timeout (or (:timeout request) timeout)))))

(defn- generate-form-data [params]
  (conj (mapv (fn [[k v]]
                (if (coll? v)
                  (str content-disposition k "\"; filename=\"" (second v) "\"\n"
                    "Content-Type: application/octet-stream\n\n"
                    (first v))
                  (str content-disposition k "\"\n\n" v))) params) "--\n"))

(defn- generate-multipart-body [boundary body-parts]
  (->> body-parts
    (map str (repeat boundary))
    (interpose "\n")
    (apply str)))

(defn- boundary [c]
  (apply str (cons c (take 10 (repeatedly #(int (rand 10)))))))

(defn- wrap-multipart-params [client]
  (fn [{:keys [multipart-params] :as request}]
    (if multipart-params
      (let [b    (boundary boundary-constant)
            body (generate-multipart-body b (generate-form-data multipart-params))]
        (client (-> request
                  (dissoc :multipart-params)
                  (assoc :content-type (str "multipart/form-data; boundary=" b))
                  (assoc :body body))))
      (client request))))

(defn- wrap-throw-on-error [client]
  (fn [request]
    (let [response (client request)]
      (if-let [error (:error response)]
        (throw (js/Error. error))
        response))))

(defn- wrap-add-method [client method]
  (fn [request]
    (client (assoc request :method (string/upper-case (name method))))))

(defn- wrap-to-from-js [client]
  (fn [request]
    (-> request
      clj->js
      client
      (js->clj :keywordize-keys true))))

(defn- do-request [client]
  (fn [opts]
    (client opts)))

(defn- request [client method url opts]
  ((-> client
     do-request
     wrap-to-from-js
     wrap-throw-on-error
     wrap-debug
     wrap-accepts
     wrap-content-type
     wrap-add-content-length
     wrap-form-params
     wrap-multipart-params
     (wrap-add-timeout default-timeout)
     wrap-add-headers
     (wrap-add-method method)) (assoc opts :url url)))

(defn get
  "Performs a GET request. It takes an URL and an optional map of options.
  These include:
  :timeout, number, default 5 seconds
  :debug, boolean, assoc the request on to the response
  :insecure, proceed even if the connection is considered insecure
  :accepts, keyword or string. Valid keywords are :json or :xml
  :content-type, keyword or string Valid keywords are :json or :xml
  :headers, map, a map containing headers
  :socket, string, specifying a system path to a socket to use
  :binary-response, boolean, encode response body as vector of unsigned bytes"
  ([url] (get url {}))
  ([url opts] (request js/PLANCK_REQUEST :get url opts)))

(s/def ::timeout integer?)
(s/def ::debug boolean?)
(s/def ::insecure boolean?)
(s/def ::accepts (s/or :kw #{:json :xml} :str string?))
(s/def ::content-type (s/or :kw #{:json :xml} :str string?))
(s/def ::headers (s/and map? (fn [m]
                               (and (every? keyword? (keys m))
                                    (every? string? (vals m))))))
(s/def ::socket string?)
(s/def ::binary-response boolean?)
(s/def ::body (s/or :string string? :binary vector?))
(s/def ::status integer?)

(s/fdef get
  :args (s/cat :url string? :opts (s/? (s/keys :opt-un
                                               [::timeout ::debug ::accepts ::content-type ::headers ::socket ::binary-response ::insecure])))
  :ret (s/keys :req-un [::body ::headers ::status]))

(defn head
  "Performs a HEAD request. It takes an URL and an optional map of options.
  These include:
  :timeout, number, default 5 seconds
  :debug, boolean, assoc the request on to the response
  :insecure, proceed even if the connection is considered insecure
  :headers, map, a map containing headers
  :socket, string, specifying a system path to a socket to use"
  ([url] (head url {}))
  ([url opts] (request js/PLANCK_REQUEST :head url opts)))

(s/fdef head
  :args (s/cat :url string? :opts (s/? (s/keys :opt-un [::timeout ::debug ::headers ::socket ::insecure])))
  :ret (s/keys :req-un [::headers ::status]))

(defn delete
  "Performs a DELETE request. It takes an URL and an optional map of options.
  These include:
  :timeout, number, default 5 seconds
  :debug, boolean, assoc the request on to the response
  :insecure, proceed even if the connection is considered insecure
  :headers, map, a map containing headers
  :socket, string, specifying a system path to a socket to use"
  ([url] (delete url {}))
  ([url opts] (request js/PLANCK_REQUEST :delete url opts)))

(s/fdef delete
  :args (s/cat :url string? :opts (s/? (s/keys :opt-un [::timeout ::debug ::headers ::socket ::insecure])))
  :ret (s/keys :req-un [::headers ::status]))

(defn post
  "Performs a POST request. It takes an URL and an optional map of options
  These options include the options for get in addition to:
  :form-params, a map, will become the body of the request, urlencoded
  :multipart-params, a list of tuples, used for file-upload
                     {:multipart-params [[\"name\" \"value\"]
                                         [\"name\" [\"content\" \"filename\"]]"
  ([url] (post url {}))
  ([url opts] (request js/PLANCK_REQUEST :post url opts)))

(s/def ::form-params map?)
(s/def ::multipart-params seq?)

(s/fdef post
  :args (s/cat :url string? :opts (s/? (s/keys :opt-un [::timeout ::debug ::accepts ::content-type ::headers ::body
                                                        ::form-params ::multipart-params ::socket ::insecure])))
  :ret (s/keys :req-un [::body ::headers ::status]))

(defn put
  "Performs a PUT request. It takes an URL and an optional map of options
  These options include the options for get in addition to:
  :form-params, a map, will become the body of the request, urlencoded
  :multipart-params, a list of tuples, used for file-upload
                     {:multipart-params [[\"name\" \"value\"]
                                         [\"name\" [\"content\" \"filename\"]]"
  ([url] (put url {}))
  ([url opts] (request js/PLANCK_REQUEST :put url opts)))

(s/fdef put
  :args (s/cat :url string? :opts (s/? (s/keys :opt-un [::timeout ::debug ::accepts ::content-type ::headers ::body
                                                        ::form-params ::multipart-params ::socket ::insecure])))
  :ret (s/keys :req-un [::body ::headers ::status]))

(defn patch
  "Performs a PATCH request. It takes an URL and an optional map of options
  These options include the options for get in addition to:
  :form-params, a map, will become the body of the request, urlencoded
  :multipart-params, a list of tuples, used for file-upload
                     {:multipart-params [[\"name\" \"value\"]
                                         [\"name\" [\"content\" \"filename\"]]"
  ([url] (patch url {}))
  ([url opts] (request js/PLANCK_REQUEST :patch url opts)))

(s/fdef patch
  :args (s/cat :url string? :opts (s/? (s/keys :opt-un [::timeout ::debug ::accepts ::content-type ::headers ::body
                                                        ::form-params ::multipart-params ::socket ::insecure])))
  :ret (s/keys :req-un [::body ::headers ::status]))
