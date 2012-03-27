(ns cemerick.friend.workflows
  (:require [cemerick.friend :as friend])
  (:use [clojure.string :only (trim)])
  (:import org.apache.commons.codec.binary.Base64))

(defn find-credential-fn
  [local-credential-fn request workflow]
  (or local-credential-fn
      (-> request ::friend/auth-config :credential-fn)
      (throw (IllegalArgumentException. (str "No :credential-fn available for " (name workflow))))))

(defn http-basic-deny
  [realm request]
  {:status 401
   :headers {"Content-Type" "text/plain"
             "WWW-Authenticate" (format "Basic realm=\"%s\"" realm)}})

(defn http-basic
  [& {:keys [credential-fn realm]}]
  (fn [{{:strs [authorization]} :headers :as request}]
    (when authorization
      (if-let [[[_ username password]] (try (-> (subs authorization 6)  ; trimming "Basic "
                                              trim
                                              (.getBytes "UTF-8")
                                              Base64/decodeBase64
                                              (String. "UTF-8")
                                              (#(re-seq #"([^:]+):(.*)" %)))
                                         (catch Exception e
                                           ; could let this bubble up and have an error page take over,
                                           ;   but basic is going to be for API usage, so...
                                           ; TODO should figure out logging for widely-used library; just use tools.logging?
                                           (.printStackTrace e)))]
      (if-let [user-record ((find-credential-fn credential-fn request :http-basic)
                             ^{::friend/workflow :http-basic} {:username username
                                                               :password password})]
        (with-meta user-record {::friend/workflow :http-basic
                                ::friend/transient true
                                :type ::friend/auth})
        (http-basic-deny realm request))
      {:status 400 :body "Malformed Authorization header for HTTP Basic authentication."}))))

(defn interactive-form-deny
  [login-uri {:keys [params] :as request}]
  (ring.util.response/redirect (let [param (str "&login_failed=Y&username=" (:username params))]
                                 (str (if (.contains login-uri "?") login-uri (str login-uri "?"))
                                      param))))

(defn interactive-form
  [& {:keys [login-uri credential-fn login-failure-handler]
      :or {login-uri "/login"}}]
  (fn [{:keys [uri request-method params] :as request}]
    (when (and (= login-uri uri)
               (= :post request-method))
      (let [{:keys [username password] :as creds} (select-keys params [:username :password])]
        (if-let [user-record (and username password
                                  ((find-credential-fn credential-fn request :interactive-form)
                                    (with-meta creds {::friend/workflow :interactive-form})))]
          (with-meta user-record {::friend/workflow :interactive-form
                                  :type ::friend/auth})
          ((or login-failure-handler
               (partial #'interactive-form-deny login-uri)) request))))))

