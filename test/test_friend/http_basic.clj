(ns test-friend.http-basic
  (:require [cemerick.friend :as friend])
  (:use clojure.test
        ring.mock.request
        [cemerick.friend.workflows :only (http-basic interactive-form)]))

(deftest basic-workflow
  (let [req (request :get "/uri")
        auth "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==" ; shamelessly ripped from wikipedia :-P
        got-creds (atom nil)]
    (is (nil? (http-basic {:realm "friend-test"} req)))
    
    (println "Don't worry, an exception is expected here:")
    (is (= 400 (:status (http-basic nil (header req "Authorization" "")))))
    
    (let [auth (http-basic {:realm "friend-test" :credential-fn (fn [{:keys [username password] :as creds}]
                                                                  (is (= "Aladdin" username))
                                                                  (is (= "open sesame" password))
                                                                  (is (= :http-basic (::friend/workflow creds)))
                                                                  (reset! got-creds true)
                                                                  {:identity username})}
                 (header req "Authorization" auth))]
      (is @got-creds)
      (is (= auth {:identity "Aladdin"
                   ::friend/workflow :http-basic
                   ::friend/transient true})))
    
    (is (= {:status 401, :headers {"Content-Type" "text/plain", "WWW-Authenticate" "Basic realm=\"friend-test\""}}
           (http-basic {:realm "friend-test" :credential-fn (constantly nil)}
                       (header req "Authorization" auth))))))

(deftest form-workflow
  (let [got-creds (atom false)
        login-uri "/my_login"
        form-handler (interactive-form :login-uri login-uri
                                       :credential-fn (fn [{:keys [username password] :as creds}]
                                                        (is (= :interactive-form (::friend/workflow creds)))
                                                        (reset! got-creds true)
                                                        (when (and (= "open sesame" password)
                                                                   (= "Aladdin" username))
                                                          {:identity username})))]
    (is (nil? (form-handler (request :get login-uri))))
    
    (is (= {:status 302
            :headers {"Location" "/my_login?&login_failed=Y&username="}
            :body ""}
           (form-handler (request :post login-uri))))
    
    (is (= {:status 302
            :headers {"Location" "/my_login?&login_failed=Y&username=foo"}
            :body ""}
           (form-handler (assoc (request :post login-uri)
                                :params {:username "foo"}))))
    
    (is (= {::friend/workflow :interactive-form, :identity "Aladdin"}
           (form-handler (assoc (request :post login-uri)
                                :params {:username "Aladdin"
                                         :password "open sesame"}))))))
