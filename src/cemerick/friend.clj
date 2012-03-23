(ns cemerick.friend
  (:require [clojure.set :as set])
  (:use (ring.util [response :as response :only (redirect)])))

(defn- original-url
  [{:keys [scheme server-name server-port uri query-string]}]
  (str (name scheme) "://" server-name
       (cond
         (and (= :http scheme) (not= server-port 80)) (str \: server-port)
         (and (= :https scheme) (not= server-port 443)) (str \: server-port)
         :else nil)
       uri
       (when (seq query-string)
         (str \? query-string))))

(def ^:dynamic *default-scheme-ports* {:http 80 :https 443})

(defn requires-scheme
  "Ring middleware that requires that the given handler be accessed using
   the specified scheme (:http or :https), a.k.a. channel security.
   Will use the optional map of scheme -> port numbers to determine the
   port to redirect to (defined in *default-scheme-ports*).

       (requires-scheme ring-handler :https)

   ...will redirect an http request to the same uri, but with an https
   scheme and default 443 port.

       (requires-scheme ring-handler :https {:https 8443})

   ...will redirect an http request to the same uri, but with an https
   scheme and to port 8443."
  ([handler scheme]
    (requires-scheme handler scheme *default-scheme-ports*))
  ([handler scheme scheme-mapping]
    (fn [request]
      (if (= (:scheme request) scheme)
        (handler request)
        ; TODO should this be permanent 301?
        (redirect (original-url (assoc request
                                  :scheme scheme
                                  :server-port (scheme-mapping scheme))))))))

(defn- retain-auth*
  [m auth]
  (assoc-in m [:session] ::auth auth))

(defn- logout*
  [response]
  (assoc-in response [:session ::auth] false))

(defn logout
  [handler]
  #(logout* (handler %)))

(def ^:dynamic *current-auth*
  "A threadlocal reference to the value of (-> request :session ::auth)."
  nil)

(defn- default-unauthorized-handler
  [request]
  {:status 401})

(defn get-auth
  "Returns the ::auth(entication) entry for the given request or response."
  [m]
  (-> m :session ::auth))

(defn auth-config
  [request]
  (::auth-config request))

(defn authenticate
  [{:keys [retain-auth allow-anon unauthorized-handler
           credential-fn workflows] :as config
    :or {retain-auth true allow-anon true
         credential-fn (constantly nil)
         unauthorized-handler #'default-unauthorized-handler}}
   handler]
  (fn [request]
    (if-let [auth (or (get-auth request)
                      (->> (map #(% (assoc request ::auth-config config)) workflows)
                        (filter boolean)
                        first))]
      (binding [*current-auth* auth]
        (let [resp (handler (retain-auth* request auth))]
        (if (and retain-auth
                 ;; some workflows shouldn't be retained, or retaining them
                 ;; serves no purpose (e.g. http basic)
                 (not (::transient auth))
                 ;; a false auth is used by logout or nested non-retention; anything
                 ;; else produced by a handler is assumed to either be functional
                 ;; maintenance of the existing authentication or a login escalation
                 ;; that we want to percolate up anyway
                 (nil? (get-auth resp)))
          (retain-auth* resp auth)
          (logout* resp))))
      (if allow-anon
        (handler request)
        (unauthorized-handler request)))))

(defn authorize
  [{:keys [roles]} handler]
  (fn [request]
    (let [auth (get-auth request)
          granted-roles (:roles auth)]
      (if (seq (set/intersection roles granted-roles))
        (handler request)
        ((-> request auth-config :unauthorized-handler) request)))))
