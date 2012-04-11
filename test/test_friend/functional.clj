(ns test-friend.functional
  (:require [clj-http.client :as http])
  (:use clojure.test
        ring.adapter.jetty
        [slingshot.slingshot :only (throw+ try+)]
        [test-friend.mock-app :only (mock-app mock-app-realm users page-bodies)]))

(declare test-port)

(defn- run-test-app
  [f]
  (let [server (ring.adapter.jetty/run-jetty #'mock-app {:port 0 :join? false})
        port (-> server .getConnectors first .getLocalPort)]
    (def test-port port)  ;; would use with-redefs, but can't test on 1.2
    (try
      (f)
      (finally
        (.stop server)))))

(use-fixtures :once run-test-app)

(defn url
  [uri]
  (str "http://localhost:" test-port uri))

(defn urls
  [& uris]
  (map vector uris (map url uris)))

(deftest access-anons
  (doseq [[uri url] (urls "/" "/login")
          :let [resp (http/get url)]]
    (is (http/success? resp))
    (is (= (page-bodies uri) (:body resp))))
  
  (let [api-resp (http/get (url "/free-api") {:as :json})]
    (is (http/success? api-resp))
    (is (= {:data 99} (:body api-resp)))))

(deftest ok-404
  (try+
    (http/get (url "/wat"))
    (assert false)
    (catch [:status 404] {:keys [body]}
      (is (= "404" body)))))

(deftest login-redirect
  (doseq [[uri url] (urls "/auth-api" "/echo-roles" "/hook-admin"
                    "/user/account" "/user/private-page" "/admin")
          :let [resp (http/get url)]]
    (is (= (page-bodies "/login") (:body resp)) uri)))

(deftest http-basic-invalid
  (try+
    (http/get (url "/auth-api") {:basic-auth "foo:bar"})
    (assert false) ; should never get here
    (catch [:status 401] {{:strs [www-authenticate]} :headers}
      (is (= www-authenticate (str "Basic realm=\"" mock-app-realm \"))))))

(deftest http-basic
  (let [{:keys [body]} (http/get (url "/auth-api") {:basic-auth "api-key:api-pass"
                                                    :as :json})]
    (is (= {:data 42} body))))

(defn- check-user-role-access
  "Used to verify hierarchical determination of authorization; both
   admin and user roles should be able to access these URIs."
  []
  (are [uri] (is (= (page-bodies uri) (:body (http/get (url uri)))))
       "/user/account"
       "/user/private-page"))

(deftest user-login
  (binding [clj-http.core/*cookie-store* (clj-http.cookies/cookie-store)]
    (is (= (page-bodies "/login") (:body (http/get (url "/user/account")))))
    (let [resp (http/post (url "/login")
                 {:form-params {:username "jane" :password "user_password"}})]
      ; ensure that previously-requested page is redirected to upon redirecting authentication
      ; clj-http *should* redirect us, but isn't yet; working on it: 
      ; https://github.com/dakrone/clj-http/issues/57
      (is (http/redirect? resp))
      (is (= "/user/account" (-> resp :headers (get "location")))))
    (check-user-role-access)
    (is (= {:roles ["test-friend.mock-app/user"]} (:body (http/get (url "/echo-roles") {:as :json}))))
    
    ; deny on admin and api roles
    (try+
      (http/get (url "/admin"))
      (assert false) ; should never get here
      (catch [:status 401] _
        (is true)))
    (try+
      (http/get (url "/auth-api"))
      (assert false) ; should never get here
      (catch [:status 401] _
        (is true)))
    
    
    (testing "logout blocks access to privileged routes"
      (is (= (page-bodies "/") (:body (http/get (url "/logout")))))
      (is (= (page-bodies "/login") (:body (http/get (url "/user/account"))))))))

(deftest hooked-authorization
  (binding [clj-http.core/*cookie-store* (clj-http.cookies/cookie-store)]
    (http/post (url "/login") {:form-params {:username "jane" :password "user_password"}})
    (try+
      (http/get (url "/hook-admin"))
      (assert false) ; should never get here
      (catch [:status 401] resp
        (is (= "Sorry, you do not have access to this resource." (:body resp)))))
    
    (http/post (url "/login") {:form-params {:username "root" :password "admin_password"}})
    (is (= (page-bodies "/hook-admin")) (http/get (url "/hook-admin")))))

(deftest admin-login
  (binding [clj-http.core/*cookie-store* (clj-http.cookies/cookie-store)]
    (is (= (page-bodies "/login") (:body (http/get (url "/admin")))))
    
    (http/post (url "/login") {:form-params {:username "root" :password "admin_password"}})
    (is (= (page-bodies "/admin")) (http/get (url "/admin")))
    (check-user-role-access)
    (is (= {:roles ["test-friend.mock-app/admin"]} (:body (http/get (url "/echo-roles") {:as :json}))))))

(deftest logout-only-on-correct-uri
  ;; logout middleware was previously being applied eagerly
  (binding [clj-http.core/*cookie-store* (clj-http.cookies/cookie-store)]
    (is (= (page-bodies "/login") (:body (http/get (url "/admin")))))
    (http/post (url "/login") {:form-params {:username "root" :password "admin_password"}})
    
    (try+
      (http/get (url "/wat"))
      (assert false)
      (catch [:status 404] e))
    (is (= (page-bodies "/admin")) (http/get (url "/admin")))
    
    (is (= (page-bodies "/")) (http/get (url "/logout")))
    (is (= (page-bodies "/login") (:body (http/get (url "/admin")))))))

;;;; TODO
; requires-scheme
; su
