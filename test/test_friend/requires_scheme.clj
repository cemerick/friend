(ns test-friend.requires-scheme
  (:use clojure.test
        ring.mock.request
        [cemerick.friend :only (requires-scheme requires-scheme-with-proxy *default-scheme-ports*)]))

(deftest test-channel-security
  (doseq [ports [nil {:http 8080 :https 8443}]
          p [:http :https]
          :let [other ({:http :https :https :http} p)]]
    (let [h (binding [*default-scheme-ports* (or ports *default-scheme-ports*)]
              (requires-scheme (constantly "response") p))
          {{:strs [Location]} :headers :as resp} (h (assoc (request :get "/any?a=5") :scheme other))
          location (and Location (java.net.URL. Location))]
      (is (= 302 (:status resp)))
      (is (= (name p) (.getProtocol location)))
      (is (= (get ports p -1) (.getPort location)))
      (is (= "/any" (.getPath location)))
      (is (= "a=5" (.getQuery location))))))

(deftest test-channel-security-with-proxy
  (doseq [ports [nil {:http 8080 :https 8443}]
          p [:http :https]
          :let [other ({:http :https :https :http} p)]]
    (let [h (binding [*default-scheme-ports* (or ports *default-scheme-ports*)]
              (requires-scheme-with-proxy (constantly "response") p))
          {{:strs [Location]} :headers :as resp} (h (assoc (header (request :get "/any?a=5") "x-forwarded-proto" other) :scheme :http))
          location (and Location (java.net.URL. Location))]
      (is (= 302 (:status resp)))
      (is (= (name p) (.getProtocol location)))
      (is (= (get ports p -1) (.getPort location)))
      (is (= "/any" (.getPath location)))
      (is (= "a=5" (.getQuery location))))))
