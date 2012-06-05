(ns cemerick.friend
  (:require [clojure.set :as set])
  (:use (ring.util [response :as response :only (redirect)])
        [slingshot.slingshot :only (throw+ try+)])
  (:refer-clojure :exclude (identity)))

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

(defn merge-authentication
  "If the workflow has modified the session and passed it in meta, use it.
  Then update the authentications."
  [m auth]
  (let [new-session (or (-> auth meta :session) (:session m))]
    (assoc m :session (update-in new-session [::identity]
                              #(-> (assoc-in % [:authentications (:identity auth)] auth)
                                   (assoc :current (:identity auth)))))))

(defn- logout*
  [response]
  (update-in response [:session] dissoc ::identity))

(defn logout
  "Ring middleware that modifies the response to drop all retained authentications."
  [handler]
  #(when-let [response (handler %)]
     (logout* response)))

(defn- default-unauthorized-handler
  [request]
  {:status 401
   :body "Sorry, you do not have access to this resource."})

(defn identity
  "Returns the identity associated with the given request or response.
   This will either be nil (for an anonymous user/session) or a map
   containing:

     :current - the name of the current authentication, must be a key into
                the map in the :authentications slot
     :authentications - a map with values of authentication maps keyed
                by their :identity."
  [m]
  (-> m :session ::identity))

(defn auth?
  "Returns true only if the argument is an authentication map (i.e. has
   a (type) of ::auth)."
  [x]
  (= ::auth (type x)))

(def ^{:dynamic true
       :doc "A threadlocal reference to the value of (identity request).

This is fundamentally here only to support `authorize` and its derivatives.
In general, you should not touch this; use `authentications` to obtain the
current authentications from the Ring request."}
      *identity* nil)

(defn current-authentication
  ([] (current-authentication *identity*))
  ([identity]
    (-> identity :authentications (get (:current identity)))))

(def ^{:doc "Returns true only if the provided request/response has no identity.
Equivalent to (complement current-authentication)."}
      anonymous? (complement current-authentication))

;; ring.util.response/response?, pulled from ring v1.1.0-beta
(defn- ring-response?
  [resp]
  (and (map? resp)
       (integer? (:status resp))
       (map? (:headers resp))))

(defn- ring-response
  [resp]
  (if (ring-response? resp)
    resp
    (response/response resp)))

(defn- clear-identity
  [response]
  (if (:session response)
    (assoc-in response [:session ::identity] nil)
    response))

(defn- redirect-new-auth
  [authentication-map request]
  (when (::redirect-on-auth? (meta authentication-map) true)
    (let [unauthorized-uri (-> request :session ::unauthorized-uri)
          resp (response/redirect-after-post (or unauthorized-uri (-> request ::auth-config :default-landing-uri)))]
      (if unauthorized-uri
        (-> resp
          (assoc :session (:session request))
          (update-in [:session] dissoc ::unauthorized-uri))
        resp))))

(defn- authenticate*
  [handler
   {:keys [retain-auth? allow-anon? unauthorized-redirect-uri unauthorized-handler
           default-landing-uri credential-fn workflows login-uri] :as config
    :or {retain-auth? true, allow-anon? true
         default-landing-uri "/"
         login-uri "/login"
         credential-fn (constantly nil)
         unauthorized-handler #'default-unauthorized-handler}}
   request]
  (let [request (assoc request ::auth-config config)
        workflow-result (->> (map #(% request) workflows)
                          (filter boolean)
                          first)]
      (if (and workflow-result (not (auth? workflow-result)))
        ;; workflow assumed to be a ring response
        workflow-result
        (let [new-auth? (auth? workflow-result)
              request (if new-auth?
                        (merge-authentication request workflow-result)
                        request)
              auth (identity request)]
          (binding [*identity* auth]
            (if (not (or auth allow-anon?))
              (unauthorized-handler request)
              (try+
                (if new-auth?
                  (-> (or (redirect-new-auth workflow-result request)
                          (handler request))
                    ring-response
                    (assoc-in [:session ::identity] auth))
                  (handler request))
                (catch [:type ::unauthorized] error-map
                  ;; TODO log unauthorized access at trace level
                  (if (or auth (not unauthorized-redirect-uri))
                    (unauthorized-handler request)
                    (-> (ring.util.response/redirect unauthorized-redirect-uri)
                      (assoc :session (:session request))
                      (assoc-in [:session ::unauthorized-uri] (:uri request))))))))))))

(defn authenticate
  [ring-handler auth-config]
  ; keeping authenticate* separate is damn handy for debugging hooks, etc.
  #(authenticate* ring-handler auth-config %))

;; TODO
#_(defmacro role-case
  [])

(defn throw-unauthorized
  [identity & {:as authorization-info}]
  (throw+ (merge {:type ::unauthorized
                  ::identity identity}
                 authorization-info)))

(defmacro authenticated
  [& body]
  `(if (current-authentication *identity*)
     ~@body
     (#'throw-unauthorized *identity* :exprs (quote [~@body]))))

(defn authorized?
  "Returns the first value in the :roles of the current authentication
   in the given identity map that isa? one of the required roles.
   Returns nil otherwise, indicating that the identity is not authorized
   for the set of required roles."
  [roles identity]
  (let [granted-roles (-> identity current-authentication :roles)]
    (first (for [granted granted-roles
                 required roles
                 :when (isa? granted required)]
             granted))))

(defmacro authorize
  "Macro that only allows the evaluation of the given body of code if the
   currently-identified user agent has a role that isa? one of the roles
   in the provided set.  Otherwise, control will be
   thrown up to the unauthorized-handler configured in the `authenticate`
   middleware.

   Note that this macro depends upon the *identity* var being bound to the
   current user's authentications.  This will work fine in e.g. agent sends
   and futures and such, but will fall down in places where binding conveyance
   don't apply (e.g. lazy sequences, direct java.lang.Thread usages, etc)."
  [roles & body]
  `(let [roles# ~roles]
     (if (authorized? roles# *identity*)
       (do ~@body)
       (throw-unauthorized *identity* :required-roles roles# :exprs (quote [~@body])))))

(defn authorize-hook
  "Authorization function suitable for use as a hook with robert-hooke library.
   This allows you to place access controls around a function defined in code
   you don't control.

   e.g.

   (add-hook #'restricted-function (partial #{::admin} authorize-hook))

   Like `authorize`, this depends upon *identity* being bound appropriately."
  ;; that example will result in the hook being applied multiple times if
  ;; loaded in a REPL multiple times — but, authorize-hook composes w/o a problem
  [roles f & args]
  (authorize roles (apply f args)))

(defn wrap-authorize
  "Ring middleware that only passes a request to the given handler if the
   identity in the request has a role that isa? one of the roles
   in the provided set.  Otherwise, the request will be handled by the
   unauthorized-handler configured in the `authenticate` middleware.

   Tip: make sure your authorization middleware is applied *within* your routing
   layer!  Otherwise, authorization failures could occur simply because of the 
   ordering of your routes, or on 404 requests.  Using something like Compojure's
   `context` makes such arrangements easy to maintain."
  [handler roles]
  (fn [request]
    (if (authorized? roles (identity request))
      (handler request)
      (throw-unauthorized (identity request)
                          :wrapped-handler handler
                          :roles roles))))