(ns cemerick.friend.credentials
  (:require [clojure.edn]
            [clojure.tools.trace :refer :all])
  (:import (org.mindrot.jbcrypt BCrypt)
           (org.apache.commons.codec.binary Base64)
           (java.util UUID)))

(defn hash-bcrypt
  "Hashes a given plaintext password using bcrypt and an optional
   :work-factor (defaults to 10 as of this writing).  Should be used to hash
   passwords included in stored user credentials that are to be later verified
   using `bcrypt-credential-fn`."
  [password & {:keys [work-factor]}]
  (BCrypt/hashpw password (if work-factor
                            (BCrypt/gensalt work-factor)
                            (BCrypt/gensalt))))

(defn bcrypt-verify
  "Returns true if the plaintext [password] corresponds to [hash],
the result of previously hashing that password."
  [password hash]
  (BCrypt/checkpw password hash))

(defn- remember-me-str [{:keys [username password-hash expiration-time salt]}]
  (str "username=" username
       ":password-hash=" password-hash
       ":expiration-time=" expiration-time
       ":salt=" salt))

(defn- encode-remember-me-hash-cookie-value
  "generate the cookie value made of:
  base64({:username username
          :expiration-time expiration-time
          :hash (bcrypt {:username username
                         :expiration-time expiration-time
                         :password-hash password-hash
                         :key key})})
  with:
  * username:        the id of the user
  * password-hash:   the user password hash as found in the creds
  * expiration-time: the time when the cookie will expire expressed in milliseconds
  * key:             a unique key (salt) to prevent modification of the cookie hash, defined with a java random UUID
  validity-duration is a duration in milliseconds that define the expiration time (now + validation duration = expiration time)"
  [{:keys [username password-hash expiration-time salt] :as all}]
  (Base64/encodeBase64String
   (.getBytes
    (str "{:username \"" username "\""
         " :expiration-time " expiration-time
         " :hash \"" (hash-bcrypt (remember-me-str all)) "\"}"))))

(defn decode-remember-me-hash-cookie-value
  "decode a cookie value previously encoded with the fn generate-remember-me-has-cookie-value"
  [cookie-value]
  (try
    (clojure.edn/read-string
     (String. (Base64/decodeBase64 cookie-value)))
    (catch RuntimeException e nil)))

(defn remember-me [save-remember-me-fn! creds]
  (let [remember-me-data {:username (:username creds)
                          :password-hash (:password creds)
                          :expiration-time (+ (System/currentTimeMillis) 2592000000);;30 days
                          :salt (.toString (UUID/randomUUID))}
        cookie-value (encode-remember-me-hash-cookie-value remember-me-data)]
    (save-remember-me-fn! (:username creds) remember-me-data)
    (dissoc (merge creds (assoc remember-me-data :remember-me-cookie-value cookie-value)) :salt :password-hash)))

(defn bcrypt-credential-fn
  "A bcrypt credentials function intended to be used with `cemerick.friend/authenticate`
   or individual authentication workflows.  You must supply a function of one argument
   that will look up stored user credentials given a username/id.  e.g.:

   (authenticate {:credential-fn (partial bcrypt-credential-fn load-user-record)
                  :other :config ...}
     ring-handler-to-be-secured)

   The credentials map returned by the provided function will only be returned if
   the provided (cleartext, user-supplied) password matches the hashed
   password in the credentials map.

   The password in the credentials map will be looked up using a :password
   key by default; if the credentials-loading function will return a credentials
   map that stores the hashed password under a different key, it must specify
   that alternative key via a :cemerick.friend.credentials/password-key slot
   in the map's metadata.  So, if a credentials map looks like:

     {:username \"joe\" :app.foo/passphrase \"bcrypt hash\"}

   ...then the hash will be verified correctly as long as the credentials
   map contains a [:cemerick.friend.credentials/password-key :app.foo/passphrase]
   entry."
  ([load-credentials-fn {:keys [username password remember-me?] :as provided-user-data}]
     (bcrypt-credential-fn load-credentials-fn nil provided-user-data))
  ([load-credentials-fn save-remember-me-fn! {:keys [username password remember-me? remember-me-short-life?] :as provided-user-data}]
     (when-let [creds (load-credentials-fn username)]
       (let [password-key (or (-> creds meta ::password-key) :password)]
         (when (bcrypt-verify password (get creds password-key))
           (if remember-me?
             (dissoc (remember-me save-remember-me-fn! creds) password-key)
             (dissoc creds password-key)))))))

(defn remember-me-hash-fn
  [load-credentials-fn
   load-rem-me-credentials-fn
   {:keys [remember-me-cookie-value]}]
  (if-let [{:keys [username expiration-time hash]} (decode-remember-me-hash-cookie-value remember-me-cookie-value)]
    (when-let [creds (load-credentials-fn username)]
      (when-let [{exp-time-from-storage :expiration-time
                  salt-from-storage :salt} (load-rem-me-credentials-fn username)]
        (let [password-key (or (-> creds meta ::password-key) :password)
              password-from-creds (get creds password-key)
              rem-me-data {:username username
                           :expiration-time exp-time-from-storage
                           :salt salt-from-storage
                           :password-hash password-from-creds}
              creds-with-rem-me (merge rem-me-data creds)
              not-expired? (fn [exp]  (> exp (System/currentTimeMillis)))]
          (when (and (bcrypt-verify (remember-me-str rem-me-data) hash)
                     (not-expired? expiration-time))
            (dissoc creds-with-rem-me password-key :salt :password-hash)))))))
