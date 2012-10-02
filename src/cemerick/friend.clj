(ns cemerick.friend
  (:require [clojure.set :as set])
  (:use (ring.util [response :as response :only (redirect)])
        [slingshot.slingshot :only (throw+ try+)]
        [clojure.core.incubator :only (-?>)])
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

(defn requires-scheme-with-proxy
  "Ring middleware similar to friend/requires-scheme that should be
  able to handle things like load balancers in Amazon's elastic
  beanstalk and heroku in addition to other load balancers and reverse
  proxies that use x-forwarded-proto and thus don't set :scheme in the
  request map properly. Do not use if your application server is
  directly facing the internet as these headers are *very* easy to
  forge."
  ([handler scheme]
     (requires-scheme-with-proxy handler scheme *default-scheme-ports*))
  ([handler scheme scheme-mapping]
     (fn [request]
       (if (= (get-in request [:headers "x-forwarded-proto"]) (name scheme))
         (handler request)
         (redirect (original-url
                    (assoc request
                      :scheme scheme
                      :server-port (scheme-mapping scheme))))))))


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
obtain the current authentications from the Ring request."}
      *identity* nil)

(defn current-authentication
  ([] (current-authentication *identity*))
  ([identity]
    (-> identity :authentications (get (:current identity)))))

(def ^{:doc "Returns true only if the provided request/response has no identity.
Equivalent to (complement current-authentication)."}
      anonymous? (complement current-authentication))

(defn- ring-response
  [resp]
  (if (response/response? resp)
    resp
    (response/response resp)))

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
                     (and (string? redirect) redirect)
                     (-> request ::auth-config :default-landing-uri)))]
      (if unauthorized-uri
        (-> resp
          (assoc :session (:session request))
          (update-in [:session] dissoc ::unauthorized-uri))
        resp))))

(defn- redirect-unauthorized
  [redirect-uri request]
  (-> (ring.util.response/redirect redirect-uri)
    (assoc :session (:session request))
    (assoc-in [:session ::unauthorized-uri] (:uri request))))

(defn- authenticate*
  [handler config request]
  (let [{:keys [allow-anon? unauthorized-handler workflows login-uri] :as config}
        (merge {:allow-anon? true
                :default-landing-uri "/"
                :login-uri "/login"
                :credential-fn (constantly nil)
                :workflows []
                :unauthorized-handler #'default-unauthorized-handler}
               config)
        request (assoc request ::auth-config config)
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
            (if (and (not auth) (not allow-anon?))
              (redirect-unauthorized login-uri request)
              (try+
                (if-not new-auth?
                  (handler request)
                  (-?> (or (redirect-new-auth workflow-result request)
                           (handler request))
                    (ensure-identity request)))
                (catch ::type error-map
                  ;; TODO log unauthorized access at trace level
                  (if auth
                    (unauthorized-handler (assoc request
                                                 ::authorization-failure
                                                 error-map))
                    (redirect-unauthorized login-uri request))))))))))

(defn authenticate
  [ring-handler auth-config]
  ; keeping authenticate* separate is damn handy for debugging hooks, etc.
  #(authenticate* ring-handler auth-config %))

;; TODO
#_(defmacro role-case
  [])

(defn throw-unauthorized
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
   an auxillary map that is merged to it as the first form of the body
   of code wrapped by `authenticated`."
  [& body]
  (let [[unauthorized-info body] (if (map? (first body)) body [nil body])]
    `(if (current-authentication *identity*)
       ~@body
       (#'throw-unauthorized *identity* (merge ~unauthorized-info
                                               {::exprs (quote [~@body])
                                                ::type :unauthenticated})))))

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

   The exception that causes this change in control flow carries a map of
   data describing the authorization failure; you can optionally provide
   an auxillary map that is merged to it as the first form of the body
   of code wrapped by `authorize`.

   Note that this macro depends upon the *identity* var being bound to the
   current user's authentications.  This will work fine in e.g. agent sends
   and futures and such, but will fall down in places where binding conveyance
   don't apply (e.g. lazy sequences, direct java.lang.Thread usages, etc)."
  [roles & body]
  (let [[unauthorized-info & body] (if (map? (first body)) body (cons nil body))]
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
  (fn [request]
    (if (authorized? roles (identity request))
      (handler request)
      (throw-unauthorized (identity request)
                          {::wrapped-handler handler
                           ::required-roles roles}))))