(ns test-friend.credentials
  (:require [cemerick.friend.credentials :as creds])
  (:use clojure.test))

(deftest simple
  (is (= {:username "joe"}
         (creds/bcrypt-credential-fn
          {"username" {:username "joe" :password (creds/hash-bcrypt "foo")}}
          {:username "username" :password "foo"}))))

(deftest custom-password-key
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
