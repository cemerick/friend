(ns cemerick.friend.credentials
  (:require [crypto.password.bcrypt :as bcrypt]
            [crypto.password.pbkdf2 :as pbkdf2]))

(defn build-credential-fn
  "Builds a credential function from a verify function that checks if
  the plaintext password in the first argument hashes to the second
  argument"
  [verify-fn]
  (fn [load-credentials-fn {:keys [username password]}]
    (when-let [creds (load-credentials-fn username)]
      (let [password-key (or (-> creds meta ::password-key) :password)]
        (when (verify-fn password (get creds password-key))
          (dissoc creds password-key))))))

(defn hash-bcrypt
  "Hashes a given plaintext password using bcrypt and an optional
   :work-factor (defaults to 10 as of this writing).  Should be used to hash
   passwords included in stored user credentials that are to be later verified
   using `bcrypt-credential-fn`."
  [password & {:keys [work-factor]}]
  (if work-factor
    (bcrypt/encrypt password work-factor)
    (bcrypt/encrypt password)))

(defn bcrypt-verify
  "Returns true if the plaintext [password] corresponds to [hash],
the result of previously hashing that password."
  [password hash]
  (bcrypt/check password hash))

(def bcrypt-credential-fn
  "A bcrypt credentials function intended to be used with
  `cemerick.friend/authenticate` or individual authentication
  workflows.  You must supply a function of one argument that will
  look up stored user credentials given a username/id.  e.g.:

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

  ...then the hash will be verified correctly as long as the
  credentials map contains
  a [:cemerick.friend.credentials/password-key :app.foo/passphrase]
  entry."
  (build-credential-fn bcrypt-verify))

(defn hash-pbkdf2
  [password & {:keys [iterations salt]}]
  (if iterations
    (if salt
      (pbkdf2/encrypt password iterations salt)
      (pbkdf2/encrypt password iterations))
    (pbkdf2/encrypt password)))

(defn pbkdf2-verify
  [password hash]
  (pbkdf2/check password hash))

(def pbkdf2-credential-fn
  (build-credential-fn pbkdf2-verify))
