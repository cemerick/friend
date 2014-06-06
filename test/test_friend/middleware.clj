(ns test-friend.middleware
  (:use clojure.test
        [compojure.core :only (defroutes GET)]
        [cemerick.friend :only (wrap-authorize)]))

(defroutes ^{:private true} routes
  (GET "/path" request "Response"))

(deftest wrap-authorize-throws-on-empty-role-set
  (testing "wrap-authorize throws IllegalArgumentException on empty roles set"
    (is (thrown? IllegalArgumentException (wrap-authorize routes #{})))))

