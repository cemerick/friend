(ns cemerick.friend.openid
  (:require [cemerick.friend :as friend]
            [cemerick.friend.workflows :as workflows]
            [cemerick.friend.util :as util]
            clojure.walk
            ring.util.response
            [ring.util.request :as req]
            [ring.util.codec :as codec]
            [clojure.core.cache :as cache])
  (:use [cemerick.friend.util :only (gets)])
  (:import (org.openid4java.consumer ConsumerManager VerificationResult
                           InMemoryConsumerAssociationStore
                           InMemoryNonceVerifier)
           (org.openid4java.message Message AuthRequest ParameterList MessageException)
           (org.openid4java.message.ax AxMessage FetchRequest FetchResponse)
           (org.openid4java.message.sreg SRegMessage SRegRequest SRegResponse)))

(def ^{:private true} ax-props
  {"country" "http://axschema.org/contact/country/home"
   "email" "http://axschema.org/contact/email"  ;"http://schema.openid.net/contact/email".
   "firstname" "http://axschema.org/namePerson/first"
   "language" "http://axschema.org/pref/language"
   "lastname" "http://axschema.org/namePerson/last"})

(def ^{:private true} sreg-attrs
  ["nickname", "email", "fullname", "dob",
   "gender", "postcode", "country", "language", "timezone"])

(defn- request-attribute-exchange
  [^AuthRequest auth-req]
  ;; might as well carpet-bomb for attributes
  (doto auth-req
    (.addExtension (reduce #(doto % (.addAttribute %2 true))
                           (SRegRequest/createFetchRequest)
                           sreg-attrs))
    (.addExtension (reduce (fn [fr [k v]]
                             (.addAttribute fr k v true)
                             fr)
                           (FetchRequest/createFetchRequest)
                           ax-props))))

(defn- return-url
  [request]
  (or (::return-url request)
      (util/original-url request)))

(defn- handle-init
  [^ConsumerManager mgr discovery-cache user-identifier {:keys [session] :as request} realm]
  (let [discoveries (.discover mgr user-identifier)
        provider-info (.associate mgr discoveries)
        return-url (return-url request)
        auth-req (request-attribute-exchange
                  (if realm
                    (.authenticate mgr provider-info return-url realm)
                    (.authenticate mgr provider-info return-url)))
        discovery-key (str (java.util.UUID/randomUUID))]
    (swap! discovery-cache assoc discovery-key provider-info)
    (assoc (ring.util.response/redirect (.getDestinationUrl auth-req true))
      :session (assoc session ::openid-disc discovery-key))))

(defn- gather-attr-maps
  [^Message response]
  (for [[type ext-uri ext-class attr-keys] [[::sreg SRegMessage/OPENID_NS_SREG SRegResponse sreg-attrs]
                                            [::ax AxMessage/OPENID_NS_AX FetchResponse (keys ax-props)]]]
    ;; SReg fails for wordpress and other providers because they don't sign the attribute values
    ;; http://en.forums.wordpress.com/topic/wordpresscom-openid-endpoint-does-not-sign-sreg-attributes
    (when-let [ext (try
                     (and (.hasExtension response ext-uri)
                          (.getExtension response ext-uri))
                     (catch MessageException e
                       ;; TODO LOGGING!
                       (println (format "Could not obtain %s attributes for response from %s, continuing..."
                                        type (.getOpEndpoint response)))
                       (.printStackTrace e)))]
      (when (instance? ext-class ext)  ;; is this ever necessary? yanked from the examples...
        (->> attr-keys
          (map #(when-let [v (.getAttributeValue ext %)] [(keyword %) v]))
          (into ^{:type type} {}))))))

(defn- build-credentials
  [^VerificationResult verification]
  (when-let [identification (some-> verification .getVerifiedId .getIdentifier)]
    (let [response (.getAuthResponse verification)]
      (reduce merge (cons {:identity identification} (gather-attr-maps response))))))

;; we end up leaving a string in the user session, but at least it's not an unreadable,
;; unprintable org.openid4java.discovery.DiscoveryInformation object
(defn- handle-return
  [^ConsumerManager mgr discovery-cache {:keys [params session] :as req} openid-config]
  (let [provider-info (get @discovery-cache (::openid-disc session))
        url (return-url req)
        plist (ParameterList. params)
        credentials (build-credentials (.verify mgr url plist provider-info))]
    (swap! discovery-cache cache/evict (::openid-disc session))
    (or ((gets :credential-fn openid-config (::friend/auth-config req)) credentials)
        ((gets :login-failure-handler openid-config (::friend/auth-config req)) req))))

(defn- remove-user-identifier-param [req user-identifier-param-name]
  (letfn [(remove-user-identifier [req k]
            (update-in req [k] dissoc user-identifier-param-name))
          (rebuild-query-string [req]
            (assoc req :query-string (codec/form-encode (:query-params req))))]
    (-> req
        (remove-user-identifier :query-params)
        (remove-user-identifier :params)
        (rebuild-query-string))))

(defn workflow
  [& {:keys [openid-uri credential-fn user-identifier-param max-nonce-age
             login-failure-handler realm consumer-manager]
      :or {openid-uri "/openid"
           user-identifier-param "identifier"
           max-nonce-age 60000}
      :as openid-config}]
  (let [mgr (or consumer-manager
                (doto (ConsumerManager.)
                  (.setAssociations (InMemoryConsumerAssociationStore.))
                  (.setNonceVerifier (InMemoryNonceVerifier. (/ max-nonce-age 1000)))))
        discovery-cache (atom (cache/ttl-cache-factory {} :ttl max-nonce-age))]
    (fn [{:keys [ request-method params] :as request}]
      (when (=  (req/path-info request) openid-uri)
        (let [params (clojure.walk/stringify-keys params)
              user-identifier-param (name user-identifier-param)
              user-identifier (and (#{:post :get} request-method)
                                   (get params user-identifier-param))
              request (remove-user-identifier-param request user-identifier-param)]
          (cond
            user-identifier
            (handle-init mgr discovery-cache user-identifier request
                         (gets :realm openid-config (::friend/auth-config request)))

            (contains? params "openid.return_to")
            (if-let [auth-map (handle-return mgr discovery-cache
                                             (assoc request :params params) openid-config)]
              (vary-meta auth-map merge {::friend/workflow :openid
                                         :type ::friend/auth})
              ((or (gets :login-failure-handler openid-config (::friend/auth-config request))
                   #'workflows/interactive-login-redirect)
                (update-in request [::friend/auth-config] merge openid-config)))

            ;; TODO correct response code?
            :else ((gets :login-failure-handler openid-config (::friend/auth-config request))
                    request)))))))
