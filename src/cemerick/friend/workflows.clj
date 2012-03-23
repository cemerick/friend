(ns cemerick.friend.workflows
  (:require [cemerick.friend :as friend]
            [clojure.data.codec.base64 :as base64])
  (:use [clojure.string :only (trim)]))

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
  [{:keys [credential-fn realm]}
   {{:strs [authorization]} :headers :as request}]
  (when authorization
    (if-let [[[_ username password]] (try (-> (subs authorization 6)  ; trimming "Basic "
                                              trim
                                              (.getBytes "UTF-8")
                                              base64/decode
                                              (String. "UTF-8")
                                              (#(re-seq #"([^:]+):(.*)" %)))
                                         (catch Exception e
                                           ; could let this bubble up and have an error page take over,
                                           ;   but basic is going to be for API usage, so...
                                           ; TODO should figure out logging for widely-used library; just use tools.logging?
                                           (.printStackTrace e)))]
      (if-let [user-record ((find-credential-fn credential-fn request :http-basic)
                             {:username username
                              :password password
                              ::friend/workflow :http-basic})]
        (assoc user-record
          ::friend/workflow :http-basic
          ::friend/transient true)
        (http-basic-deny realm request))
      {:status 400 :body "Malformed Authorization header for HTTP Basic authentication."})))