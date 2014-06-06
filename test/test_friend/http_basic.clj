(ns test-friend.http-basic
  (:require [cemerick.friend :as friend])
  (:use clojure.test
        ring.mock.request
        [cemerick.friend.workflows :as workflows :only (http-basic)]))

(deftest basic-workflow
  (let [req (request :get "/uri")
        auth "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==" ; shamelessly ripped from wikipedia :-P
        got-creds (atom nil)]
    (is (= {:status 401, :headers {"Content-Type" "text/plain"
                                   "WWW-Authenticate" "Basic realm=\"friend-test\""}}
          (workflows/http-basic-deny "friend-test" req)))

    (is (= 400 (:status ((http-basic) (header req "Authorization" "Basic BadAuthHeader")))))

    ;; it's not basic auth, so should return nil since it could be another valid auth scheme
    (is (= nil (:status ((http-basic) (header req "Authorization" "SomeOtherAuthHeader")))))

    (let [auth ((http-basic :realm "friend-test" :credential-fn (fn [{:keys [username password] :as creds}]
                                                                  (is (= "Aladdin" username))
                                                                  (is (= "open sesame" password))
                                                                  (is (= :http-basic (::friend/workflow (meta creds))))
                                                                  (reset! got-creds true)
                                                                  {:identity username}))
                 (header req "Authorization" auth))]
      (is @got-creds)
      (is (= {:identity "Aladdin"} auth))
      (is (= (meta auth) {::friend/workflow :http-basic
                          ::friend/redirect-on-auth? false
                          ::friend/ensure-session false
                          :type ::friend/auth})))

    (testing "empty usernames and passwords are per the spec"
      (let [auth ((http-basic :realm "friend-test"
                              :credential-fn (fn [{:keys [username password]}]
                                               (is (= "" username password))
                                               {:identity username}))
                   ; 0g== is (base64 ":")
                   (header req "Authorization" "Basic Og=="))]
        (is (= {:identity ""} auth))))

    (is (= {:status 401, :headers {"Content-Type" "text/plain"
                                   "WWW-Authenticate" "Basic realm=\"friend-test\""}}
          ((http-basic :realm "friend-test" :credential-fn (constantly nil))
            (header req "Authorization" auth))))))
