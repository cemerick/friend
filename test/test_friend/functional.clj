(ns test-friend.functional
  (:require [clj-http.client :as http])
  (:use clojure.test
        ring.adapter.jetty
        [slingshot.slingshot :only (throw+ try+)]
        [test-friend.mock-app :only (mock-app mock-app-realm users
                                      page-bodies missles-fired?)]))

(declare test-port)

(defn run-test-app
  [app f]
  (let [server (ring.adapter.jetty/run-jetty app {:port 0 :join? false})
        port (-> server .getConnectors first .getLocalPort)]
    (def test-port port)  ;; would use with-redefs, but can't test on 1.2
    (reset! missles-fired? false)
    (try
      (f)
      (finally
        (.stop server)))))

(use-fixtures :once (partial run-test-app #'mock-app))

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
  (doseq [[uri url] (urls "/echo-roles" "/hook-admin"
                    "/user/account" "/user/private-page" "/admin")
          :let [resp (http/get url)]]
    (is (= (page-bodies "/login") (:body resp)) uri)))

(defn- check-user-role-access
  "Used to verify hierarchical determination of authorization; both
   admin and user roles should be able to access these URIs."
  []
  (are [uri] (is (= (page-bodies uri) (:body (http/get (url uri)))))
       "/user/account"
       "/user/private-page"))

(deftest user-login
  (binding [clj-http.core/*cookie-store* (clj-http.cookies/cookie-store)]
    (is (= (page-bodies "/login") (:body (http/get (url "/user/account?query-string=test")))))
    (let [resp (http/post (url "/login")
                 {:form-params {:username "jane" :password "user_password"}})]
      ; ensure that previously-requested page is redirected to upon redirecting authentication
      ; clj-http *should* redirect us, but isn't yet; working on it: 
      ; https://github.com/dakrone/clj-http/issues/57
      (is (http/redirect? resp))
      (is (= (url "/user/account?query-string=test") (-> resp :headers (get "location")))))
    (check-user-role-access)
    (is (= {:roles ["test-friend.mock-app/user"]} (:body (http/get (url "/echo-roles") {:as :json}))))
    
    ; deny on admin role
    (try+
      (http/get (url "/admin"))
      (assert false) ; should never get here
      (catch [:status 403] _
        (is true)))
    
    (testing "logout blocks access to privileged routes"
      (is (= (page-bodies "/") (:body (http/get (url "/logout")))))
      (is (= (page-bodies "/login") (:body (http/get (url "/user/account"))))))))

(deftest session-integrity
  (testing (str "that session state set elsewhere is not disturbed by friend's operation, "
                "and that maintaining the friend identity doesn't disturb session state")
    (binding [clj-http.core/*cookie-store* (clj-http.cookies/cookie-store)]
      (let [post-session-data #(:body (http/post (url "/session-value")
                                                 {:form-params {:value %}}))
            get-session-data #(:body (http/get (url "/session-value")))]
        (is (= "session-data" (post-session-data "session-data")))
        (is (= "session-data" (get-session-data)))
        (http/post (url "/login")
                   {:form-params {:username "jane" :password "user_password"}})
        (check-user-role-access)
        (is (= "session-data" (get-session-data)))
        (is (= "auth-data" (post-session-data "auth-data")))
        (is (= "auth-data" (get-session-data)))
        (check-user-role-access)
        
        (http/get (url "/logout"))
        (let [should-be-login-redirect (http/get (url "/user/account")
                                                 {:follow-redirects false})]
          (is (= 302 (:status should-be-login-redirect)))
          (is (re-matches #"http://localhost:\d+/login"
                (-> should-be-login-redirect :headers (get "location")))))
        ; TODO should logout blow away the session completely?
        (is (= "auth-data" (get-session-data)))))))

(deftest hooked-authorization
  (binding [clj-http.core/*cookie-store* (clj-http.cookies/cookie-store)]
    (http/post (url "/login") {:form-params {:username "jane" :password "user_password"}})
    (try+
      (http/get (url "/hook-admin"))
      (assert false) ; should never get here
      (catch [:status 403] resp
        (is (= "Sorry, you do not have access to this resource." (:body resp)))))
    
    (http/post (url "/login") {:form-params {:username "root" :password "admin_password"}})
    (is (= (page-bodies "/hook-admin") (:body (http/get (url "/hook-admin")))))))

(deftest authorization-failure-available-to-handler
  (binding [clj-http.core/*cookie-store* (clj-http.cookies/cookie-store)]
    (http/post (url "/login") {:form-params {:username "jane" :password "user_password"}})
    (try+
      (http/get (url "/fire-missles"))
      (is false "should not get here")
      (catch [:status 403] resp
        (is (= "403 message thrown with unauthorized stone" (:body resp)))))
    (is (not @missles-fired?))))

(deftest admin-login
  (binding [clj-http.core/*cookie-store* (clj-http.cookies/cookie-store)]
    (is (= (page-bodies "/login") (:body (http/get (url "/admin")))))
    
    (http/post (url "/login") {:form-params {:username "root" :password "admin_password"}})
    (is (= (page-bodies "/admin") (:body (http/get (url "/admin")))))
    (check-user-role-access)
    (is (= {:roles ["test-friend.mock-app/admin"]} (:body (http/get (url "/echo-roles") {:as :json}))))))

(deftest admin-login-fn-role
  (binding [clj-http.core/*cookie-store* (clj-http.cookies/cookie-store)]
    (http/post (url "/login") {:form-params {:username "root-fn-role" :password "admin_password"}})
    (check-user-role-access)))

(deftest logout-only-on-correct-uri
  ;; logout middleware was previously being applied eagerly
  (binding [clj-http.core/*cookie-store* (clj-http.cookies/cookie-store)]
    (is (= (page-bodies "/login") (:body (http/get (url "/admin")))))
    (http/post (url "/login") {:form-params {:username "root" :password "admin_password"}})
    
    (try+
      (http/get (url "/wat"))
      (assert false)
      (catch [:status 404] e))
    (is (= (page-bodies "/admin") (:body (http/get (url "/admin")))))
    
    (is (= (page-bodies "/") (:body (http/get (url "/logout")))))
    (is (= (page-bodies "/login") (:body (http/get (url "/admin")))))))

;;;; TODO
; requires-scheme
; su
