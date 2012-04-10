(ns test-friend.http-basic
  (:require [cemerick.friend :as friend])
  (:use clojure.test
        ring.mock.request
        [cemerick.friend.workflows :only (http-basic)]))

(deftest basic-workflow
  (let [req (request :get "/uri")
        auth "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==" ; shamelessly ripped from wikipedia :-P
        got-creds (atom nil)]
    (is (nil? ((http-basic :realm "friend-test") req)))
    
    (println "Don't worry, an exception is expected here:")
    (is (= 400 (:status ((http-basic) (header req "Authorization" "BadAuthHeader")))))
    
    (let [auth ((http-basic :realm "friend-test" :credential-fn (fn [{:keys [username password] :as creds}]
                                                                  (is (= "Aladdin" username))
                                                                  (is (= "open sesame" password))
                                                                  (is (= :http-basic (::friend/workflow (meta creds))))
                                                                  (reset! got-creds true)
                                                                  {:identity username}))
                 (header req "Authorization" auth))]
      (is @got-creds)
      (is (= auth {:identity "Aladdin"}))
      (is (= (meta auth) {::friend/workflow :http-basic
                          ::friend/redirect-on-auth? false
                          :type ::friend/auth})))
    
    (is (= {:status 401, :headers {"Content-Type" "text/plain", "WWW-Authenticate" "Basic realm=\"friend-test\""}}
           ((http-basic :realm "friend-test" :credential-fn (constantly nil))
             (header req "Authorization" auth))))))
