(ns cemerick.friend.openid
  (:require [cemerick.friend :as friend]
            [cemerick.friend.workflows :as workflows]
            clojure.walk
            ring.util.response)
  (:use clojure.core.incubator
        [cemerick.friend.util :only (gets)])
  (:import (org.openid4java.consumer ConsumerManager VerificationResult
                           InMemoryConsumerAssociationStore
                           InMemoryNonceVerifier)
           (org.openid4java.message Message AuthRequest ParameterList MessageException)
           (org.openid4java.message.ax AxMessage FetchRequest FetchResponse)
           (org.openid4java.message.sreg SRegMessage SRegRequest SRegResponse)))

(def ^{:private true} common-identifiers
  {"yahoo" "https://me.yahoo.com"
   "google" "https://www.google.com/accounts/o8/id"})

(def ^{:private true} ax-props
  {"country" "http://axschema.org/contact/country/home"
   "email"	"http://axschema.org/contact/email"  ;"http://schema.openid.net/contact/email".
   "firstname" "http://axschema.org/namePerson/first"
   "language"	"http://axschema.org/pref/language"
   "lastname"	"http://axschema.org/namePerson/last"})

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

(def ^{:private true} return-key "auth_return")

(defn- handle-init
  [^ConsumerManager mgr user-identifier {:keys [session] :as request} realm]
  (let [discoveries (.discover mgr user-identifier)
        provider-info (.associate mgr discoveries)
        return-url (str (#'friend/original-url request) "?" return-key "=1")
        auth-req (request-attribute-exchange
                   (if realm
                     (.authenticate mgr provider-info return-url realm)
                     (.authenticate mgr provider-info return-url)))]
    (assoc (ring.util.response/redirect (.getDestinationUrl auth-req true))
      :session (assoc session ::openid-disc provider-info))))

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
  (when-let [identification (-?> verification .getVerifiedId .getIdentifier)]
    (let [response (.getAuthResponse verification)]
      (reduce merge (cons {:identity identification} (gather-attr-maps response))))))

(defn- handle-return
  [^ConsumerManager mgr {:keys [params session] :as req} openid-config]
  (let [provider-info (::openid-disc session)
        url (#'friend/original-url req)
        plist (ParameterList. params)
        credentials (build-credentials (.verify mgr url plist provider-info))]
    (or ((gets :credential-fn openid-config (::friend/auth-config req)) credentials)
        ((gets :login-failure-handler openid-config (::friend/auth-config req)) req))))

(defn workflow
  [& {:keys [openid-uri credential-fn user-identifier-param max-nonce-age
             login-failure-handler realm]
      :or {openid-uri "/openid"
           user-identifier-param "identifier"
           max-nonce-age 60}
      :as openid-config}]
  (let [mgr (doto (ConsumerManager.)
              (.setAssociations (InMemoryConsumerAssociationStore.))
              (.setNonceVerifier (InMemoryNonceVerifier. max-nonce-age)))]
    (fn [{:keys [uri request-method params session] :as request}]
      (when (= uri openid-uri)
        (let [params (clojure.walk/stringify-keys params)
              user-identifier (and (= request-method :post)
                                   (get params (name user-identifier-param)))]
          (cond
            user-identifier
            (handle-init mgr user-identifier request (gets :realm openid-config (::friend/auth-config request)))
            
            (contains? params return-key)
            (if-let [auth-map (handle-return mgr (assoc request :params params) openid-config)]
              (vary-meta auth-map merge {::friend/workflow :openid
                                         :session (dissoc session ::openid-disc)
                                         :type ::friend/auth})
              ((or (gets :login-failure-handler openid-config (::friend/auth-config request)) #'workflows/interactive-login-redirect)
                (update-in request [::friend/auth-config] merge openid-config)))
            
            ;; TODO correct response code?
            :else (login-failure-handler request)))))))
