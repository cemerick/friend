(ns cemerick.friend.credentials
  (:require [crypto.password.bcrypt :as bcrypt]
            [crypto.password.pbkdf2 :as pbkdf2]
            [crypto.password.scrypt :as scrypt]))

(defn- build-credential-fn
  "Builds a credential function from the given verify function. The
  verify function must accept two arguments and verifies that the
  plaintext password in the first argument hashes to the second
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
  "Returns true if the plaintext [password] corresponds to [hash], the
  result of previously hashing that password with bcrypt."
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
  "Hashes the given plaintext password using pbkdf2. An optional
  number of :iterations can be specified (defaults to 100,000) and, if
  the number of iterations is specified, an optional :salt can also
  be specified (defaults to 8 random bytes). Should be used to hash
  passwords included in stored user credentials that are to be later
  verified using `pbkdf2-credential-fn`."
  [password & {:keys [iterations salt]}]
  (if iterations
    (if salt
      (pbkdf2/encrypt password iterations salt)
      (pbkdf2/encrypt password iterations))
    (pbkdf2/encrypt password)))

(defn pbkdf2-verify
  "Returns true if the plantext [password] corresponds to [hash], the
  result of previously hashing that password with pbkdf2."
  [password hash]
  (pbkdf2/check password hash))

(def pbkdf2-credential-fn
  "A pbkdf2 credentials function intended to be used with
  `cemerick.friend/authenticate` or individual authentication
  workflows. You must supply a function of one argument that will look
  up stored user credentials given a username/id. e.g.:

    (authenticate {:credential-fn (partial pbkdf2-credential-fn load-user-record)
                   :other :config ...}
       ring-handler-to-be-secured)

  If the provided (cleartext, user-supplied) password matches the
  hashed password in the credentials map, that map is returned.

  The password in the credentials map will be looked up using
  a :password key by default; if the credentials-loading function will
  return a credentials map that stores the hashed password under a
  different key, it must specify that alternative key via
  a :cemerick.friend.credentials/password-key slot in the map's
  metadata.  So, if a credentials map looks like:

  {:username \"joe\" :app.foo/passphrase \"bcrypt hash\"}

  ...then the hash will be verified correctly as long as the
  credentials map contains
  a [:cemerick.friend.credentials/password-key :app.foo/passphrase]
  entry."
  (build-credential-fn pbkdf2-verify))

(defn hash-scrypt
  "Hashes the given plaintext password using scrypt. an optional :cpu
  cost parameter (defaults to 2^15) can be specified, but it must be a
  power of 2. If the :cpu parameter is specified, then
  optional :para (defaults to 1) and :ram (defaults to 8) parameters
  can be specified for the memory cost and para factor respectively"
  [password & {:keys [cpu ram para]}]
  (if cpu
    (if (and ram para)
      (scrypt/encrypt password cpu ram para)
      (scrypt/encrypt password cpu))
    (scrypt/encrypt password)))

(defn scrypt-verify
  "Returns true if the plaintext [password] corresponds to [hash], the
  result of previously hashing that password with scrypt."
  [password hash]
  (scrypt/check password hash))

(def scrypt-credential-fn
  "A scrypt credentials function intended to be used with
  `cemerick.friend/authenticate` or individual authentication
  workflows. You must supply a function of one argument that will look
  up stored user credentials given a username/id. e.g.:

  (authenticate {:credential-fn (partial scrypt-credential-fn load-user-record)
                 :other :config ...}
    ring-handler-to-be-secured)

  If the provided (cleartext, user-supplied) password matches the
  hashed password in the credentials map, that map is returned.

  The password in the credentials map will be looked up using
  a :password key by default; if the credentials-loading function will
  return a credentials map that stores the hashed password under a
  different key, it must specify that alternative key via
  a :cemerick.friend.credentials/password-key slot in the map's
  metadata.  So, if a credentials map looks like:

  {:username \"joe\" :app.foo/passphrase \"bcrypt hash\"}

  ...then the hash will be verified correctly as long as the
  credentials map contains
  a [:cemerick.friend.credentials/password-key :app.foo/passphrase]
  entry."
  (build-credential-fn scrypt-verify))
