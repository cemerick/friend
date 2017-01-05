(ns cemerick.friend
  (:require [cemerick.friend.util :as util]
            [clojure.set :as set])
  (:use (ring.util [response :as response :only (redirect)])
        [slingshot.slingshot :only (throw+ try+)])
  (:refer-clojure :exclude (identity)))

(defn merge-authentication
  [m auth]
  (update-in m [:session ::identity]
             #(-> (assoc-in % [:authentications (:identity auth)] auth)
                (assoc :current (:identity auth)))))

(defn logout*
  "Removes any Friend identity from the response's :session.
Assumes that the request's :session has already been added to the
response (doing otherwise will likely result in the Friend identity
being added back into the final response)."
  [response]
  (update-in response [:session] dissoc ::identity))

(defn logout
  "Ring middleware that modifies the response to drop all retained
authentications."
  [handler]
  #(when-let [response (handler %)]
     (->> (or (:session response) (:session %))
       (assoc response :session)
       logout*)))

(defn- default-unauthorized-handler
  [request]
  {:status 403
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
In general, you should not touch this; use the `identity` function to
obtain the identity associated with a Ring request, or e.g.
`(current-authentication request)` to obtain the current authentication
from a Ring request or response."}
      *identity* nil)

(defn current-authentication
  "Returns the authentication associated with either the current in-flight
request, or the provided Ring request or response map.

Providing the Ring request map explicitly is strongly encouraged, to avoid
any funny-business related to the dynamic binding of `*identity*`."
  ([] (current-authentication *identity*))
  ([identity-or-ring-map]
   (let [identity (or (identity identity-or-ring-map)
                     identity-or-ring-map)]
      (-> identity :authentications (get (:current identity))))))

(def ^{:doc "Returns true only if the provided request/response has no identity.
Equivalent to (complement current-authentication)."}
      anonymous? (complement current-authentication))

(defn- ensure-identity
  [response request]
  (if-let [identity (identity request)]
    (assoc response :session (assoc (or (:session response) (:session request))
                                    ::identity identity))
    response))

(defn- redirect-new-auth
  [authentication-map request]
  (when-let [redirect (::redirect-on-auth? (meta authentication-map) true)]
    (let [unauthorized-uri (-> request :session ::unauthorized-uri)
          resp (response/redirect-after-post
                 (or unauthorized-uri
                     (if (string? redirect)
                       redirect
                       (str (:context request) (-> request ::auth-config :default-landing-uri)))))]
      (if unauthorized-uri
        (-> resp
          (assoc :session (:session request))
          (update-in [:session] dissoc ::unauthorized-uri))
        resp))))

(defn default-unauthenticated-handler
  [request]
  (-> request
    ::auth-config
    :login-uri
    (#(str (:context request) %))
    (util/resolve-absolute-uri request)
    ring.util.response/redirect
    (assoc :session (:session request))
    (assoc-in [:session ::unauthorized-uri] (util/original-url request))))

(defn authenticate-response
  "Adds to the response's :session for responses with a :friend/ensure-identity-request key."
  [response request]
  (if-let [new-request (:friend/ensure-identity-request response)]
    (ensure-identity (dissoc response :friend/ensure-identity-request) new-request)
    (dissoc response :friend/ensure-identity-request)))

(defn- retry-request
  [{:keys [request new-auth? workflow-result catch-handler] :as args}]
  (try+
   (if-not new-auth?
     {:friend/handler-map (select-keys args [:request :catch-handler :auth])}
     (let [response (or (redirect-new-auth workflow-result request)
                      {:friend/handler-map (select-keys args [:request :catch-handler :auth])})]
       (if (::ensure-session (meta workflow-result) true)
         (assoc response :friend/ensure-identity-request request)
         response)))
   (catch ::type error-map
     ;; TODO log unauthorized access at trace level
     (catch-handler
      (assoc request ::authorization-failure error-map)))))

(defn- no-workflow-result-request [request config workflow-result]
  (let [{:keys [unauthenticated-handler unauthorized-handler allow-anon?]}
        (merge {:allow-anon? true
                :unauthenticated-handler #'default-unauthenticated-handler
                :unauthorized-handler #'default-unauthorized-handler} config)
        new-auth? (auth? workflow-result)
        request (if new-auth?
                  (merge-authentication request workflow-result)
                  request)
        auth (identity request)]
    (binding [*identity* auth]
      (if (and (not auth) (not allow-anon?))
        (unauthenticated-handler request)
        (retry-request
         {:request request
          :auth auth
          :new-auth? new-auth?
          :workflow-result workflow-result
          :catch-handler (if auth unauthorized-handler unauthenticated-handler)})))))

(defn handler-request
  "Calls handler with appropriate binding and error catching and returns a response."
  [handler {:keys [catch-handler request auth]}]
  (binding [*identity* auth]
    (try+
     (handler request)
     (catch ::type error-map
       (catch-handler
        (assoc request ::authorization-failure error-map))))))

(defn authenticate-request
  "Returns response if there is one. Otherwise returns a map with :friend/handler-map key
which contains a map to be called with a ring handler."
  [request config]
  (let [{:keys [workflows login-uri] :as config}
        (merge {:default-landing-uri "/"
                :login-uri "/login"
                :credential-fn (constantly nil)
                :workflows []}
               config)
        request (assoc request ::auth-config config)
        workflow-result (some #(% request) workflows)]

      (if (and workflow-result (not (auth? workflow-result)))
        ;; workflow assumed to be a ring response
        workflow-result
        (no-workflow-result-request request config workflow-result))))

(defn- authenticate*
  [ring-handler auth-config request]
  (let [response-or-handler-map (authenticate-request request auth-config)
        response (if-let [handler-map (:friend/handler-map response-or-handler-map)]
                   (handler-request ring-handler handler-map) response-or-handler-map)]
    (authenticate-response
      (when response
        (update-in response
                   [:friend/ensure-identity-request]
                   (fn [x]
                     (or x (:friend/ensure-identity-request response-or-handler-map)))))
      request)))

(defn authenticate
  [ring-handler auth-config]
  ; keeping authenticate* separate is damn handy for debugging hooks, etc.
  #(authenticate* ring-handler auth-config %))

(defn throw-unauthorized
  "Throws a slingshot stone (see `slingshot.slingshot/throw+`) containing
   the [authorization-info] map, in addition to these slots:
 
   :cemerick.friend/type - the type of authorization failure that has 
        occurred, defaults to `:unauthorized`
   :cemerick.friend/identity - the current identity, defaults to the
        provided [identity] argument

   Provided the stone is thrown within the context of an `authenticate`
   middleware, handling of the original request will be delegated to its
   `unauthorized-handler` or the `unauthenticated-handler` as appropriate,
   with the slingshot stone's map assoc'ed into the request map under
   `:cemerick.friend/authorization-failure`."
  [identity authorization-info]
  (throw+ (merge {::type :unauthorized
                  ::identity identity}
                 authorization-info)))

(defmacro authenticated
  "Macro that only allows the evaluation of the given body of code if the
   current user is authenticated. Otherwise, control will be
   thrown up to the unauthorized-handler configured in the `authenticate`
   middleware.

   The exception that causes this change in control flow carries a map of
   data describing the authorization failure; you can optionally provide
   an auxiliary map that is merged to it as the first form of the body
   of code wrapped by `authenticated`."
  [& body]
  (let [[unauthorized-info body] (if (map? (first body)) body [nil body])]
    `(if (current-authentication *identity*)
       (do ~@body)
       (#'throw-unauthorized *identity* (merge ~unauthorized-info
                                               {::exprs (quote [~@body])
                                                ::type :unauthenticated})))))

(defn authorized?
  "Returns the first value in the :roles of the current authentication
   in the given identity map that isa? one of the required roles.
   Returns nil otherwise, indicating that the identity is not authorized
   for the set of required roles. If :roles is a fn, it will be executed
   with no args and assumed to return a collection of roles."
  [roles identity]
  (let [granted-roles (-> identity current-authentication :roles)
        granted-roles (if (fn? granted-roles)
                        (granted-roles)
                        granted-roles)]

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

   The exception that causes this change in control flow carries a map of
   data describing the authorization failure (see `throw-unauthorized`).
   You can optionally provide an auxiliary map that is merged to it as the
   first form of the body of code wrapped by `authorize`, e.g.:

     (authorize #{::user :some.ns/admin}
       {:op-name \"descriptive name for secured operation\"}
        

   Note that this macro depends upon the *identity* var being bound to the
   current user's authentications.  This will work fine in e.g. agent sends
   and futures and such, but will fall down in places where binding conveyance
   doesn't apply (e.g. lazy sequences, direct java.lang.Thread usages, etc)."
  [roles & [authz-failure-map? & body]]
  (let [[unauthorized-info body] (if (and (seq body) (map? authz-failure-map?))
                                    [authz-failure-map? body]
                                    [nil (cons authz-failure-map? body)])]
    `(let [roles# ~roles]
       (if (authorized? roles# *identity*)
         (do ~@body)
         (throw-unauthorized *identity*
                             (merge ~unauthorized-info
                                    {::required-roles roles#
                                     ::exprs (quote [~@body])}))))))

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
  (if (empty? roles)
    (throw (IllegalArgumentException. "roles cannot be empty")))

  (fn [request]
    (if (authorized? roles (identity request))
      (handler request)
      (throw-unauthorized (identity request)
                          {::wrapped-handler handler
                           ::required-roles roles}))))
