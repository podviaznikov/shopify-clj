(ns shopify.resources
  (:require [clojure.string :as str]
            clj-http.core
            clj-http.client
            clj-http.links))

(defn wrap-json-format
  [client]
  (fn [req]
    (let [req (assoc req
                :accept :json
                :as :json
                :content-type :json)]
      (if-let [uri (:uri req)]
        (client (assoc req :uri (str/replace uri #"(?<!\.json)$" ".json")))
        (client req)))))

(defn wrap-access-token
  "Middleware converting an :access-token option into the appropriate auth header"
  [client]
  (fn [req]
    (if-let [token (:access-token req)]
      (client (-> req
                  (dissoc :access-token)
                  (assoc-in [:headers "x-shopify-access-token"] token)))
      (client req))))

(defn wrap-ssl
  "Middleware defaulting requests to ssl"
  [client]
  (fn [req]
    (if-let [scheme (:scheme req)]
      (client req)
      (client (-> req
                  (assoc :scheme :https))))))

(defn wrap-shop
  "Middleware converting a :shop option into :server-name"
  [client]
  (fn [req]
    (if-let [shop (:shop req)]
      (client (-> req
                  (dissoc :shop)
                  (assoc :server-name shop)))
      (client req))))

(defn wrap-retry-throttled
  "Middleware which retries a request if it's being throttled.
  
  Request options:
  
  :retry-on-throttle-errors - default:true, whether to retry throttled requests
  :throttle-retry-delay - default:60, how many seconds to wait between throttle retries
  :max-throttle-retries - default:12, how many times to retry before giving up and returning the error response
  
  In the response:
  
  :throttle-retry-count - number of retries (absent when 0)"
  [client]
  (fn [req]
    (if (= false (:retry-on-throttle-errors req))
      (client req)
      (let [wait-seconds (:throttle-retry-delay req 60)
            max-retries (:max-throttle-retries req 12)]
        (loop [retries 0]
          (let [response (client req)]
            (if (or
                  (not= 429 (:status response))
                  (= retries max-retries))
              (if (= retries 0)
                response
                (assoc response :throttle-retry-count retries))
              (do
                (Thread/sleep (* wait-seconds 1000))
                (recur (+ 1 retries))))))))))

(defn wrap-request
  [request]
  (-> request
      clj-http.client/wrap-request-timing
      wrap-retry-throttled
      
      clj-http.client/wrap-lower-case-headers
      clj-http.client/wrap-query-params
      ; clj-http.client/wrap-basic-auth
      ; clj-http.client/wrap-oauth
      ; clj-http.client/wrap-user-info
      
      
      ; clj-http.client/wrap-url
      clj-http.client/wrap-redirects
      clj-http.client/wrap-decompression
      clj-http.client/wrap-input-coercion
      ; put this before output-coercion, so additional charset
      ; headers can be used if desired
      clj-http.client/wrap-additional-header-parsing
      clj-http.client/wrap-output-coercion
      clj-http.client/wrap-exceptions
      clj-http.client/wrap-accept
      clj-http.client/wrap-accept-encoding
      clj-http.client/wrap-content-type
      clj-http.client/wrap-form-params
      clj-http.client/wrap-nested-params
      clj-http.client/wrap-method
      ; clj-http.cookies/wrap-cookies
      clj-http.links/wrap-links
      ; clj-http.client/wrap-unknown-host
      
      wrap-json-format
      wrap-access-token
      wrap-shop
      wrap-ssl
      ))

(def request
  (wrap-request clj-http.core/request))
