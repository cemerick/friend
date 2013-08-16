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
                          ::friend/redirect-on-auth? true})))))
