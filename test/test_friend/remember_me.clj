(ns test-friend.remember-me
  (:require [clojure.test :refer :all]
            [ring.adapter.jetty :refer :all]
            [clj-http.client :as http]
            [test-friend.mock-app :as mock-app :only (mock-app users page-bodies missles-fired?)]
            [spexec.core  :refer :all]))

(declare server)
(declare server-port)

(defn start-server []
  (println "start jetty server...")
  (def server (ring.adapter.jetty/run-jetty test-friend.mock-app/mock-app {:port 0 :join? false}))
  (def server-port (-> server .getConnectors first .getLocalPort))
  (println "jetty server started on port " server-port))

(defn stop-server []
  (println "stop jetty server...")
  (.stop server)
  (println "jetty server stopped"))

(defbefore (fn [] (start-server)))

(defafter (fn [] (stop-server)))

(defn url [uri] (str "http://localhost:" server-port uri))

(defwhen #"the user creates its account \{:username \"(.*)\" :password \"(.*)\"\}" [_ username password]
  (println username password))

(defgiven #"a running webapp$" [prev]
  (is (.isStarted server)))

(defwhen #"the user access the protected resource with url '(.*)'" [url-str]
 (is (= (mock-app/page-bodies "/login") (:body (http/get (url url-str))))))

(defwhen #"the user login: http POST to \"/login\" URL with previous form params"
  [prev]
  (let [resp (http/post (url "/login") {:form-params {:username (:username prev) :password (:password prev)}})]
    (is (not (.contains (get-in resp [:headers "location"]) "login_failed=Y")) "login should succeed")

    resp))

(defthen #"the user should be authenticated: http response 303 and welcome page is displayed"
  [prev]
  (do "something"))

(defthen #"the body response is the login page"  []  (do "assert the result of when step"))

(exec-spec "test/test_friend/remember-me.story")
