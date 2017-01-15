(ns test-friend.credentials
  (:require [cemerick.friend.credentials :as creds])
  (:use clojure.test))

(deftest simple
  (is (= {:username "joe"}
         (creds/bcrypt-credential-fn
           {"username" {:username "joe" :password (creds/hash-bcrypt "foo")}}
           {:username "username" :password "foo"}))))

(deftest empty-password
  (is (= nil
         (creds/bcrypt-credential-fn
          {"username" {:username "joe" :password ""}}
          {:username "username" :password "foo"}))))

(deftest empty-user-data
  (is (= nil
         (creds/bcrypt-credential-fn
          {"username" {}}
          {:username "username" :password "foo"}))))

(deftest nil-user-data
  (is (= nil
         (creds/bcrypt-credential-fn
          {"username" nil}
          {:username "username" :password "foo"}))))

(deftest custom-password-key
  (is (= nil
              (creds/bcrypt-credential-fn
                {"username" {"username" "joe" ::password (creds/hash-bcrypt "foo")}}
                {:username "username" :password "foo"})))

  (is (= {:username "joe"}
         (creds/bcrypt-credential-fn
           {"username" ^{::creds/password-key ::password}
                        {:username "joe" ::password (creds/hash-bcrypt "foo")}}
           {:username "username" :password "foo"}))))
