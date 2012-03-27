(ns cemerick.friend
  (:require [clojure.set :as set])
  (:use (ring.util [response :as response :only (redirect)])
        [slingshot.slingshot :only (throw+ try+)]))

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

(def ^{:dynamic true} *default-scheme-ports* {:http 80 :https 443})

(defn requires-scheme
  "Ring middleware that requires that the given handler be accessed using
   the specified scheme (:http or :https), a.k.a. channel security.
   Will use the optional map of scheme -> port numbers to determine the
   port to redirect to (defaults defined in *default-scheme-ports*).

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
  (assoc-in m [:session ::auth] auth))

(defn- logout*
  [response]
  (assoc-in response [:session ::auth] false))

(defn logout
  [handler]
  #(logout* (handler %)))

(defn- default-unauthorized-handler
  [request]
  {:status 401})

(defn get-auth
  "Returns the ::auth(entication) entry for the given request or response."
  [m]
  (-> m :session ::auth))

(def ^{:dynamic true
       :doc "A threadlocal reference to the value of (get-auth request)."}
      *current-auth* nil)

(defn authenticate
  [{:keys [retain-auth allow-anon unauthorized-handler
           credential-fn workflows] :as config
    :or {retain-auth true allow-anon true
         credential-fn (constantly nil)
         unauthorized-handler #'default-unauthorized-handler}}
   handler]
  (fn [request]
    (let [auth (or (get-auth request)
                   (->> (map #(% (assoc request ::auth-config config)) workflows)
                     (filter boolean)
                     first))]
      (binding [*current-auth* (and auth auth)]
        (cond
          (and (not auth) (not allow-anon)) (unauthorized-handler request)
          (and auth (not= ::auth (type auth))) auth
          :else (try+
                  (let [resp (if auth
                               (handler (retain-auth* request auth))
                               (handler request))]
                    (if (and *current-auth*
                             retain-auth
                             ;; some workflows shouldn't be retained, or retaining them
                             ;; serves no purpose (e.g. http basic)
                             (not (::transient (meta auth)))
                             ;; a false auth is used by logout or nested non-retention; anything
                             ;; else produced by a handler is assumed to either be functional
                             ;; maintenance of the existing authentication or a login escalation
                             ;; that we want to percolate up anyway
                             (nil? (get-auth resp)))
                      (retain-auth* resp *current-auth*)  ;; make way for support for multiple logins
                      (logout* resp)))
                  (catch [:type :unauthorized] error-map
                    ;; TODO again, figure out logging
                    (println error-map)
                    (unauthorized-handler request))))))))

(defn authorize*
  "Returns true if at least one role in the :roles in the given authentication map
   matches one of the roles in the provided set."
  [roles auth]
  (let [granted-roles (:roles auth)]
    (boolean (seq (set/intersection roles granted-roles)))))

(defn wrap-authorize
  "Ring middleware that ensures that the authenticated user has one of the roles
   in the given set; otherwise, the request will be handled by the
   unauthorized-handler configured in the `authenticate` middleware."
  [roles handler]
  (fn [request]
    (if (authorize* roles (get-auth request))
      (handler request)
      (throw+ {:type :unauthorized
               ::auth (get-auth request)
               :wrapped-handler handler
               :roles roles}))))

(defmacro authorize
  "Macro that allows the evaluation of the given body of code iff the authenticated
   user has one of the roles in the provided set.  Otherwise, control will be
   thrown up to the unauthorized-handler configured in the `authenticate`
   middleware.

   Note that this macro depends upon the *current-auth* binding to obtain the
   currently-authenticated user's info.  This will work fine in e.g. agent sends
   and futures and such, but will fall down in places where binding conveyance
   don't apply (e.g. lazy sequences, direct java.lang.Thread usages, etc)."
  [roles & body]
  `(let [roles# ~roles]
     (if (authorize* roles# (:roles *current-auth*))
       (do ~@body)
       (throw+ {:type :unauthorized
                ::auth *current-auth*
                :expressions (quote [~body])
                :roles roles#}))))

(defn authorize-hook
  "Authorization function suitable for use as a hook with robert-hooke library.
   This allows you to place access controls around a function defined in code
   you don't control.

   e.g.

   (add-hook #'restricted-function (partial #{:admin} authorize-hook))

   Like `authorize`, this depends upon *current-auth* being bound appropriately."
  ;; that example will result in the hook being applied multiple times if
  ;; loaded in a REPL multiple times — but, authorize-hook composes w/o a problem
  [roles f & args]
  (authorize roles (apply f args)))
