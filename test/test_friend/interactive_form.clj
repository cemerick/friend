(ns test-friend.interactive-form
  (:require [cemerick.friend :as friend])
  (:use clojure.test
        ring.mock.request
        [cemerick.friend.workflows :only (interactive-form)]))

(deftest form-workflow
  (let [got-creds (atom false)
        login-uri "/my_login"
        form-handler (interactive-form :login-uri login-uri
                                       :credential-fn (fn [{:keys [username password] :as creds}]
                                                        (is (= :interactive-form (::friend/workflow (meta creds))))
                                                        (reset! got-creds true)
                                                        (when (and (= "open sesame" password)
                                                                   (= "Aladdin" username))
                                                          {:identity username})))]
    (is (nil? (form-handler (request :get login-uri))))

    (is (= {:status 302
            :headers {"Location" "http://localhost/my_login?&login_failed=Y&username="}
            :body ""}
           (form-handler (request :post login-uri))))

    (is (= {:status 302
            :headers {"Location" "http://localhost/my_login?&login_failed=Y&username=foo"}
            :body ""}
           (form-handler (assoc (request :post login-uri)
                                :params {:username "foo"}
                                :form-params {"username" "foo"}))))

    (is (= {:status 302
            :headers {"Location" "http://localhost/my_login?&login_failed=Y&username=foobar"}
            :body ""}
           (form-handler (assoc (request :post login-uri)
                                :params {:username "foo"}
                                :form-params {"username" "foobar"}))))

    (let [auth (form-handler (assoc (request :post login-uri)
                                    :params {:username "foo"
                                             :password "open sesame"}
                                    :form-params {"username" "Aladdin"
                                                  "password" "open sesame"}))]
      (is (= auth {:identity "Aladdin"}))
      (is (= (meta auth) {::friend/workflow :interactive-form
                          :type ::friend/auth
                          ::friend/redirect-on-auth? true})))

    (testing "redirect-on-auth? can be specified when creating the workflow"
      (let [form-handler (interactive-form :login-uri login-uri
                                           :credential-fn (constantly {:identity "Aladdin"})
                                           :redirect-on-auth? false)
            auth (form-handler (assoc (request :post login-uri)
                                 :params {:username "irrelevant but necessary"
                                          :password "irrelevant but necessary"}))]
        (is (= false (::friend/redirect-on-auth? (meta auth))))))

    (testing "redirect-on-auth? is overloaded with global configuration when not specified when creating the workflow"
      (let [form-handler (interactive-form :login-uri login-uri
                                                 :credential-fn (constantly {:identity "Aladdin"}))
                  auth (form-handler (assoc (request :post login-uri)
                                       :params {:username "irrelevant but necessary"
                                                :password "irrelevant but necessary"}
                                       ::friend/auth-config {:redirect-on-auth? false}))]
              (is (= false (::friend/redirect-on-auth? (meta auth))))))

    (testing "redirect-on-auth? defaults to true when it's never specified"
      (let [form-handler (interactive-form :login-uri login-uri
                                                       :credential-fn (constantly {:identity "Aladdin"}))
                        auth (form-handler (assoc (request :post login-uri)
                                             :params {:username "irrelevant but necessary"
                                                      :password "irrelevant but necessary"}))]
                    (is (= true (::friend/redirect-on-auth? (meta auth))))))))