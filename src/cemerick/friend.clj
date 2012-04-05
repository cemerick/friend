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
  [m auth]
  (update-in m [:session ::identity]
             #(-> (assoc-in % [:authentications (:identity auth)] auth)
                (assoc :current (:identity auth)))))

(defn- logout*
  [response]
  (assoc-in response [:session ::identity] {}))

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

(defn- drop-transient-authentications
  [{:keys [current authentications] :as identity}]
  ;; some workflows shouldn't be retained, or retaining them
  ;; serves no purpose (e.g. http basic)
  (let [authentications (into {} (for [[identity auth :as p] authentications
                                       :when (not (::transient (meta auth)))]
                                   p))]
    (if (get authentications current)
        (assoc identity :authentications authentications)
        (assoc identity
               :authentications authentications
               :current (first (first authentications))))))

(defn- clear-authentications
  [response]
  (let [response (update-in response [:session] dissoc ::identity)]
    (if (empty? (:session response))
      (dissoc response :session)
      response)))

(defn retain-auth
  [auth retain-auth? response]
  (if-not retain-auth?
    (clear-authentications response)
    (let [identity (drop-transient-authentications (or (identity response) auth))]
      (if (seq (:authentications identity))
        (assoc-in response [:session ::identity] auth)
        (clear-authentications response)))))

;; ring.util.response/response?, pulled from ring v1.1.0-beta
(defn- ring-response?
  [resp]
  (and (map? resp)
       (integer? (:status resp))
       (map? (:headers resp))))

(defn- preserve-session
  [request response]
  (let [response-map (if (ring-response? response)
                       response
                       (response/response response))]
    (if (:session response-map)
      response-map
      (assoc response :session (:session request)))))

(defn authenticate
  [{:keys [retain-auth? allow-anon? unauthorized-redirect-uri unauthorized-handler
           default-landing-uri credential-fn workflows] :as config
    :or {retain-auth? true, allow-anon? true
         default-landing-uri "/"
         credential-fn (constantly nil)
         unauthorized-handler #'default-unauthorized-handler}}
   handler]
  (fn [request]
    (let [request (assoc request ::auth-config config)
          workflow-result (->> (map #(% request) workflows)
                            (filter boolean)
                            first)]
      ;(binding [*print-meta* true] (println) (prn "workflow" workflow-result))      
      (if (and workflow-result
               (not (auth? workflow-result)))
        workflow-result                             ;; workflow assumed to be a ring response
        (let [new-auth? (auth? workflow-result)
              request (if new-auth?
                        (merge-authentication request workflow-result)
                        request)
              auth (identity request)]
          #_(binding [*print-meta* true]
            (prn "session" (:session request))
            (prn auth))
          (binding [*identity* auth]
            (if (not (or auth allow-anon?))
              (unauthorized-handler request)
              (try+
                (let [[response clear-redirect-uri]
                      (if (and new-auth? (::redirect-on-auth? (meta workflow-result) true))
                        (-> (or (-> request :session ::unauthorized-uri) default-landing-uri)
                          response/redirect-after-post
                          (vector true))
                        [(handler request) false])]
                  (retain-auth auth retain-auth? (update-in
                                                   (preserve-session request response)
                                                   [:session] dissoc ::unauthorized-uri)))
                (catch [:type ::unauthorized] error-map
                  ;; TODO log unauthorized access at trace level
                  #_(println "unauthorized:" #_(or *identity* (not unauthorized-redirect-uri)) error-map)
                  #_(println "unauth resp" (assoc-in (ring.util.response/redirect unauthorized-redirect-uri)
                              [:session ::unauthorized-uri] (:uri request)))
                  (if (or auth (not unauthorized-redirect-uri))
                    (unauthorized-handler request)
                    (assoc-in (ring.util.response/redirect unauthorized-redirect-uri)
                              [:session ::unauthorized-uri] (:uri request))))))))))))

(defmacro role-case
  [])

(defn authorized?
  "Returns true if at least one role in the :roles in the given authentication map
   matches one of the roles in the provided set."
  [roles identity]
  (let [granted-roles (-> identity current-authentication :roles)]
    (boolean (seq (set/intersection roles granted-roles)))))

(defn throw-unauthorized
  [identity & {:keys [required-roles exprs]}]
  (throw+ (merge {:type ::unauthorized
                  ::identity *identity*}
            (when required-roles {:required-roles required-roles})
            (when exprs {:exprs exprs}))))

(defmacro authenticated
  [& body]
  `(if (current-authentication *identity*)
     ~@body
     (#'throw-unauthorized *identity* :exprs (quote [~@body]))))

(defmacro authorize
  "Macro that allows the evaluation of the given body of code iff the authenticated
   user has one of the roles in the provided set.  Otherwise, control will be
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

   (add-hook #'restricted-function (partial #{:admin} authorize-hook))

   Like `authorize`, this depends upon *identity* being bound appropriately."
  ;; that example will result in the hook being applied multiple times if
  ;; loaded in a REPL multiple times — but, authorize-hook composes w/o a problem
  [roles f & args]
  (authorize roles (apply f args)))

;; Ring middleware for authorization is probably not workable
;; Routes would need to be arranged very carefully to progress from least to most
;; restrictive, and hierarchical representation of roles would be a must to avoid
;; a combinatorial explosion of roles in the set used to configure the middleware
;; e.g. requests for the anon-safe /foo would result in a 401 here:
;; (routes
;;   (wrap-authorize #{:user} (GET "/account" request ...))
;;   (GET "/foo" request ...))
(defn wrap-authorize
  "Ring middleware that ensures that the authenticated user has one of the roles
   in the given set; otherwise, the request will be handled by the
   unauthorized-handler configured in the `authenticate` middleware."
  [roles handler]
  (fn [request]
    ;(println "wrap" roles (-> request identity current-authentication))
    (if (authorized? roles (identity request))
      (handler request)
      (throw+ {:type ::unauthorized
               ::identity (identity request)
               :wrapped-handler handler
               :roles roles}))))