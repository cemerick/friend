(ns cemerick.friend.workflows
  (:require [cemerick.friend :as friend]
            [cemerick.friend.util :as util])
  (:use [clojure.string :only (trim)]
        [cemerick.friend.util :only (gets)])
  (:import org.apache.commons.codec.binary.Base64))

(defn http-basic-deny
  [realm request]
  {:status 401
   :headers {"Content-Type" "text/plain"
             "WWW-Authenticate" (format "Basic realm=\"%s\"" realm)}})

(defn- username-as-identity
  [user-record]
  (if (:identity user-record)
    user-record
    (assoc user-record :identity (:username user-record))))

(defn make-auth
  "Given a user record map (presumably based on data loaded from an
   application's database), returns an authentication map that:

   * Uses the :identity or :username slot in the user record as
     the authentication map's :identity
   * Optionally merges the given auth-meta map into the authentication
     map's metadata (which by default will contain a single
     [:type :cemerick.friend/auth] entry"
  ([user-record] (make-auth user-record {}))
  ([user-record auth-meta]
    (vary-meta (username-as-identity user-record)
               merge {:type ::friend/auth} auth-meta)))

(defn http-basic
  [& {:keys [credential-fn realm] :as basic-config}]
  (fn [{{:strs [authorization]} :headers :as request}]
    (when authorization
      (if-let [[[_ username password]] (try (-> (re-matches #"\s*Basic\s+(.+)" authorization)
                                              ^String second
                                              (.getBytes "UTF-8")
                                              Base64/decodeBase64
                                              (String. "UTF-8")
                                              (#(re-seq #"([^:]*):(.*)" %)))
                                         (catch Exception e
                                           ; could let this bubble up and have an error page take over,
                                           ;   but basic is going to be used predominantly for API usage, so...
                                           ; TODO should figure out logging for widely-used library; just use tools.logging?
                                           (println "Invalid Authorization header for HTTP Basic auth: " authorization)
                                           (.printStackTrace e)))]
        (if-let [user-record ((gets :credential-fn basic-config (::friend/auth-config request))
                               ^{::friend/workflow :http-basic}
                                {:username username, :password password})]
          (make-auth user-record
                     {::friend/workflow :http-basic
                      ::friend/redirect-on-auth? false})
          (http-basic-deny realm request))
        {:status 400 :body "Malformed Authorization header for HTTP Basic authentication."}))))

(defn interactive-login-redirect
  [{:keys [params] :as request}]
  (ring.util.response/redirect
    (let [param (str "&login_failed=Y&username="
                  (java.net.URLEncoder/encode (:username params "")))
          ^String login-uri (-> request ::friend/auth-config :login-uri)]
      (util/resolve-absolute-uri
        (str (if (.contains login-uri "?") login-uri (str login-uri "?"))
          param)
        request))))

(defn interactive-form
  [& {:keys [login-uri credential-fn login-failure-handler redirect-on-auth?] :as form-config
      :or {redirect-on-auth? true}}]
  (fn [{:keys [uri request-method params] :as request}]
    (when (and (= (gets :login-uri form-config (::friend/auth-config request)) uri)
               (= :post request-method))
      (let [{:keys [username password] :as creds} (select-keys params [:username :password])]
        (if-let [user-record (and username password
                                  ((gets :credential-fn form-config (::friend/auth-config request))
                                    (with-meta creds {::friend/workflow :interactive-form})))]
          (make-auth user-record
                     {::friend/workflow :interactive-form
                      ::friend/redirect-on-auth? redirect-on-auth?})
          ((or (gets :login-failure-handler form-config (::friend/auth-config request)) #'interactive-login-redirect)
            (update-in request [::friend/auth-config] merge form-config)))))))
