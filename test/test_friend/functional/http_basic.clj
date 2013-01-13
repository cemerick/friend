(ns test-friend.functional.http-basic
  (:require  
            [clj-http.client :as http])
  (:use [test-friend.functional :as ft :only (url)]
        clojure.test
        ring.adapter.jetty
        [slingshot.slingshot :only (throw+ try+)]
        [test-friend.mock-app :only (api-app mock-app-realm)]))

(use-fixtures :once (partial ft/run-test-app #'api-app))

(deftest http-basic-invalid
  (try+
    (http/get (url "/auth-api") {:basic-auth "foo:bar"})
    (assert false) ; should never get here
    (catch [:status 401] {{:strs [www-authenticate]} :headers}
      (is (= www-authenticate (str "Basic realm=\"" mock-app-realm \"))))))

(defn- anon-request
  [path]
  (try+
    (http/get (url path))
    (assert false) ; should never get here
    (catch [:status 401] {{:strs [www-authenticate]} :headers}
      (is (= www-authenticate (str "Basic realm=\"" mock-app-realm \"))))))

(deftest http-basic-missing
  (anon-request "/auth-api")
  (anon-request "/requires-authentication"))

(deftest http-basic
  (let [{:keys [body]} (http/get (url "/auth-api") {:basic-auth "api-key:api-pass"
                                                    :as :json})]
    (is (= {:data "authorized"} body))
    (is (= {:data "anon"} (:body (http/get (url "/anon") {:as :json}))))))