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
    (with-redefs [test-port port]
      (try
        (f)
        (finally
          (.stop server))))))

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

(deftest login-redirect
  (doseq [[uri url] (urls "/auth-api" "/echo-roles" "/hook-admin"
                    "/account" "/admin")
          :let [resp (http/get url)]]
    (is (= (page-bodies "/login") (:body resp)) uri)))

(deftest http-basic-invalid
  (try+
    (http/get (url "/auth-api") {:basic-auth "foo:bar"})
    (assert false "this should never succeed")
    (catch [:status 401] {{:strs [www-authenticate]} :headers}
      (is (= (str "Basic realm=\"" mock-app-realm \"))))))

(deftest http-basic
  (let [{:keys [body cookies]} (http/get (url "/auth-api") {:basic-auth "api-key:api-pass"
                                                            :as :json})]
    (is (nil? cookies))
    (is (= {:data 42} body))))



;;;; TODO
; requires-scheme