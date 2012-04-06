(ns test-friend.mock-app
  (:require [cemerick.friend :as friend]
            (cemerick.friend [workflows :as workflows]
                             [credentials :as creds]
                             [openid :as openid])
            [cheshire.core :as json]
            [robert.hooke :as hooke]
            [hiccup.core :as hiccup]
            [ring.util.response :as resp]
            (compojure [route :as route]
                       [handler :as handler]))
  (:use [compojure.core :as compojure :only (GET ANY defroutes)]))


(def page-bodies {"/login" "Login page here."
                  "/" "Homepage."
                  "/admin" "Admin page."
                  "/user/account" "User account page."
                  "/user/private-page" "Other :user-private page."
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

(defroutes ^{:private true} anon
  (GET "/" request (page-bodies (:uri request)))
  ;; TODO move openid test into its own ns
  (GET "/test-openid" request (hiccup/html [:html
                                            [:form {:action "/openid" :method "POST"}
                                             "OpenId endpoint: "
                                             [:input {:type "text" :name "identifier"}]
                                             [:input {:type "submit" :name "login"}]]]))
  (GET "/login" request (page-bodies (:uri request)))
  (GET "/free-api" request (api-call 99))
  (friend/logout (ANY "/logout" request (resp/redirect "/"))))

(defn- admin-hook-authorized-fn
  [request]
  (page-bodies (:uri request)))

(hooke/add-hook #'admin-hook-authorized-fn
                (partial friend/authorize-hook #{:admin}))

(def ^{:private true} user-routes
  (friend/wrap-authorize #{:user}
    (compojure/routes
      (GET "/account" request (page-bodies (:uri request)))
      (GET "/private-page" request (page-bodies (:uri request))))))

(defroutes ^{:private true} interactive-secured
  (GET "/admin" request (friend/authorize #{:admin}
                          (page-bodies (:uri request))))
  (compojure/context "/user" request user-routes)
  (GET "/hook-admin" request (admin-hook-authorized-fn request))
  (GET "/echo-roles" request (friend/authenticated
                               (-> (friend/current-authentication)
                                 (select-keys [:roles])
                                 json-response))))

(defroutes ^{:private true} private-api
  (GET "/auth-api" request (friend/authorize #{:api}
                             (api-call 42))))

(def users {"root" {:username "root"
                    :password (creds/hash-bcrypt "admin_password")
                    :roles #{:admin}}
            "jane" {:username "jane"
                    :password (creds/hash-bcrypt "user_password")
                    :roles #{:user}}})

(def api-users {"api-key" {:username "api-key"
                           :password (creds/hash-bcrypt "api-pass")
                           :roles #{:api}}})

(defn- credential-fn
  [users {:keys [username password]}]
  (let [user (users username)]
    (when (and user (= password (:password user)))
      (assoc user :identity username))))

(defroutes ^{:private true} authorization-config
  anon
  interactive-secured
  private-api
  (ANY "/*" request {:status 404}))

(def mock-app
  (->> authorization-config
    (friend/authenticate {:credential-fn (partial creds/bcrypt-credential-fn users)
                          :unauthorized-redirect-uri "/login"
                          :login-uri "/login"
                          :workflows [(workflows/interactive-form
                                        :login-uri "/login")
                                      (workflows/http-basic
                                        :credential-fn (partial creds/bcrypt-credential-fn api-users)
                                        :realm mock-app-realm)
                                      ;; TODO move openid test into its own ns
                                      (openid/workflow :credential-fn (comp ring.util.response/response pr-str))]})
    handler/site))
