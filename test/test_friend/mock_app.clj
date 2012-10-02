(ns test-friend.mock-app
  (:require [cemerick.friend :as friend]
            (cemerick.friend [workflows :as workflows]
                             [credentials :as creds]
                             [openid :as openid])
            [cheshire.core :as json]
            [robert.hooke :as hooke]
            [hiccup.core :as hiccup]
            [ring.util.response :as resp]
            (compojure [handler :as handler]
                       [route :as route]))
  (:use [compojure.core :as compojure :only (GET POST ANY defroutes)]
        [clojure.core.incubator :only (-?>)]))


(def page-bodies {"/login" "Login page here."
                  "/" "Homepage."
                  "/admin" "Admin page."
                  "/user/account" "User account page."
                  "/user/private-page" "Other ::user-private page."
                  "/hook-admin" "Should be admin only."})

(def mock-app-realm "mock-app-realm")

(defn- json-response
  [x]
  (-> (json/generate-string x)
    resp/response
    (resp/content-type "application/json")))

(defn- api-call
  [value]
  (json-response {:data value}))

(defn- admin-hook-authorized-fn
  [request]
  (page-bodies (:uri request)))

(hooke/add-hook #'admin-hook-authorized-fn
                (partial friend/authorize-hook #{::admin}))

(defroutes ^{:private true} user-routes
  (GET "/account" request (page-bodies (:uri request)))
  (GET "/private-page" request (page-bodies (:uri request))))

(defroutes ^{:private true} mock-app*
  ;;;;; ANON
  (GET "/" request (page-bodies (:uri request)))
  ;; TODO move openid test into its own ns
  (GET "/test-openid" request (hiccup/html [:html
                                            [:form {:action "/openid" :method "POST"}
                                             "OpenId endpoint: "
                                             [:input {:type "text" :name "identifier"}]
                                             [:input {:type "submit" :name "login"}]]]))
  (GET "/login" request (page-bodies (:uri request)))
  (GET "/free-api" request (api-call 99))
  (friend/logout (ANY "/logout" request (resp/redirect "/")))
  
  (GET "/echo-roles" request (friend/authenticated
                               (-> (friend/current-authentication)
                                 (select-keys [:roles])
                                 json-response)))
  
  ;;;;; session integrity
  (GET "/session-value" request
       (-> request :session :session-value resp/response))
  (POST "/session-value" request
        (let [value (-> request :params :value)]
          (-> value 
            resp/response 
            (assoc :session {:session-value value}))))
  
  ;;;;; USER
  (compojure/context "/user" request (friend/wrap-authorize user-routes #{::user} ))
  
  ;;;;; ADMIN
  (GET "/admin" request (friend/authorize #{::admin}
                          (page-bodies (:uri request))))
  (GET "/hook-admin" request (admin-hook-authorized-fn request))
  (GET "/incl-auth-failure-data" request (friend/authorize #{::admin}
                                           {:response-msg "403 message thrown with unauthorized stone"}
                                           (:uri request)))
  
  (GET "/view-openid" request
       (str "OpenId authentication? " (-?> request friend/identity friend/current-authentication pr-str)))
  
  ;; FIN
  (route/not-found "404"))

(defroutes api-routes
  ;;;;; API
  (GET "/auth-api" request (friend/authorize #{:api}
                             (api-call 42))))

(def users {"root" {:username "root"
                    :password (creds/hash-bcrypt "admin_password")
                    :roles #{::admin}}
            "jane" {:username "jane"
                    :password (creds/hash-bcrypt "user_password")
                    :roles #{::user}}})

(derive ::admin ::user)

(def api-users {"api-key" {:username "api-key"
                           :password (creds/hash-bcrypt "api-pass")
                           :roles #{:api}}})

(defn- credential-fn
  [users {:keys [username password]}]
  (let [user (users username)]
    (when (and user (= password (:password user)))
      (assoc user :identity username))))

(def mock-app
  (handler/site
    (friend/authenticate
      mock-app*
      {:credential-fn (partial creds/bcrypt-credential-fn users)
       :unauthorized-handler #(if-let [msg (-> % ::friend/authorization-failure :response-msg)]
                                {:status 403 :body msg}
                                (#'friend/default-unauthorized-handler %)) 
       :workflows [(workflows/interactive-form)
                   ;; TODO move openid test into its own ns
                   (openid/workflow :credential-fn identity)]})))

(def api-app
  (handler/api
    (friend/authenticate
      api-routes
      {:allow-anon? false
       :workflows [(workflows/http-basic
                     :credential-fn (partial creds/bcrypt-credential-fn api-users)
                     :realm mock-app-realm)]})))
