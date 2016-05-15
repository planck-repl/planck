(ns planck.http
    (:refer-clojure :exclude [get])
    (:require
     [planck.core]
     [planck.io]
     [clojure.string :as string]))


(def content-types {:json            "application/json"
                    :xml             "application/xml"
                    :form-urlencoded "application/x-www-form-urlencoded"})

(def default-timeout 5)

(def boundary-constant "---------------planck-rocks-")

(def content-disposition "\nContent-Disposition: form-data; name=\"")

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

(defn generate-query-string [params]
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

(defn wrap-content-type
  "Set the appropriate Content Type header."
  [client]
  (fn [request]
    (if-let [headers (maybe-add-header request :content-type "Content-Type")]
      (-> request
          (dissoc :content-type)
          (assoc :headers headers)
          client)
      (client request))))

(defn wrap-accepts
  "Set the appropriate Accept header."
  [client]
  (fn [request]
    (if-let [headers (maybe-add-header request :accept "Accept")]
      (-> request
          (dissoc :accept)
          (assoc :headers headers)
          client)
      (client request))))

(defn wrap-debug
  "adds the request to the response if :debug is present"
  [client]
  (fn [request]
    (if-let [debug (:debug request)]
      (let [req (dissoc request :debug)]
        (assoc (client req) :request req))
      (client request))))

(defn wrap-add-content-length
  "Adds content-length if :body is present "
  [client]
  (fn [request]
    (if-let [body (:body request)]
      (let [headers (merge {"Content-length" (count body)} (:headers request))]
        (-> request
            (assoc :headers headers)
            client))
      (client request))))

(defn wrap-form-params
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

(defn wrap-add-headers
  "Adds headers to the request if they're not present"
  [client]
  (fn [request]
    (client (assoc request :headers (or (:headers request) {})))))

(defn wrap-add-timeout
  "Adds default timeout if :timeout is not present"
  [client timeout]
  (fn [request]
    (client (assoc request :timeout (or (:timeout request) timeout)))))

(defn generate-form-data [params]
  (conj (mapv (fn [[k v]]
                (if (coll? v)
                  (str content-disposition  k "\"; filename=\"" (second v) "\"\n"
                       "Content-Type: application/octet-stream\n\n"
                       (first v))
                  (str content-disposition k "\"\n\n" v ))) params) "--\n"))

(defn generate-multipart-body [boundary body-parts]
  (->> body-parts
       (map str (repeat boundary))
       (interpose "\n")
       (apply str)))

(defn boundary [c]
  (apply str (cons c (take 10 (repeatedly #(int (rand 10)))))))

(defn wrap-multipart-params [client]
  (fn [{:keys [multipart-params] :as request}]
    (if multipart-params
      (let [b (boundary boundary-constant)
            body (generate-multipart-body b (generate-form-data multipart-params))]
        (client (-> request
                    (dissoc :multipart-params)
                    (assoc :content-type (str "multipart/form-data; boundary=" b))
                    (assoc :body body))))
      (client request))))

(defn wrap-throw-on-error [client]
  (fn [request]
    (let [response (client request)]
      (if-let [error (:error response)]
        (throw (js/Error. error))
        response))))

(defn wrap-add-method [client method]
  (fn [request]
    (client (assoc request :method (string/upper-case (name method)))))) 

(defn wrap-to-from-js [client]
  (fn [request]
    (-> request
        clj->js
        client
        (js->clj :keywordize-keys true))))

(defn- do-request [client]
  (fn [opts]
    (client opts)))

(defn request [client method url opts]
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
  :accepts, keyword or string. Valid keywords are :json or :xml
  :content-type, keyword or string Valid keywords are :json or :xml
  :headers, map, a map containing headers"
  ([url] (get url {}))
  ([url opts] (request js/PLANCK_REQUEST :get url opts)))

(defn post
  "Performs a POST requeest. It takes an URL and an optional map of options
  These options include the options for get in addition to:
  :form-params, a map, will become the body of the request, urlencoded
  :multipart-params, a list of tuples, used for file-upload
                     {:multipart-params [[\"name\" \"value\"]
                                         [\"name\" [\"content\" \"filename\"]]"
  ([url] (post url {}))
  ([url opts] (request js/PLANCK_REQUEST :post url opts)))

(extend-protocol planck.io/IOFactory
  js/goog.Uri
  (make-reader [url opts]
    (let [content (atom (:body (get url opts)))]
      (letfn [(read [] (let [return @content]
                         (reset! content nil)
                         return))]
        (planck.core/->BufferedReader
         read
         (fn [])
         (atom nil)))))
  (make-writer [url opts]
    (planck.core/->Writer
     (fn [s]
       (post url {:multipart-params [[(or (:param-name opts) "file")
                                      [s (or (:filename opts) "file.pnk")]]]})
       nil)
     (fn [])
     (fn []))))

(extend-protocol planck.io/Coercions
  js/goog.Uri
  (as-url [u] u)
  (as-file [u]
    (if (= "file" (.getScheme u))
      (planck.io/as-file (.getPath u))
      (throw (js/Error. (str "Not a file: " u))))))
