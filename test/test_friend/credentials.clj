(ns test-friend.credentials
  (:require [cemerick.friend.credentials :as creds])
  (:use clojure.test))

(deftest simple-bcrypt
  (is (= {:username "joe"}
         (creds/bcrypt-credential-fn
          {"username" {:username "joe" :password (creds/hash-bcrypt "foo")}}
          {:username "username" :password "foo"}))))

(deftest custom-password-key-bcrypt
  (is (thrown? NullPointerException
               (creds/bcrypt-credential-fn
                {"username" {"username" "joe"
                             ::password (creds/hash-bcrypt "foo")}}
                {:username "username" :password "foo"})))

  (is (= {:username "joe"}
         (creds/bcrypt-credential-fn
          {"username" ^{::creds/password-key ::password}
           {:username "joe" ::password (creds/hash-bcrypt "foo")}}
          {:username "username" :password "foo"}))))


(deftest simple-pbkdf2
  (is (= {:username "joe"}
         (creds/pbkdf2-credential-fn
          {"username" {:username "joe" :password (creds/hash-pbkdf2 "foo")}}
          {:username "username" :password "foo"}))))
