(ns cemerick.friend.credentials
  (:import org.mindrot.jbcrypt.BCrypt))

(defn bcrypt-hash-password
  "Hashes a given plaintext password using bcrypt and an optional
   :work-factor (defaults to 10 as of this writing).  Should be used to hash
   passwords included in stored user credentials that are to be later verified
   using `bcrypt-credential-fn`."
  [password & {:keys [work-factor]}]
  (BCrypt/hashpw password (when work-factor
                            (BCrypt/gensalt work-factor)
                            (BCrypt/gensalt))))

(defn bcrypt-credential-fn
  "A bcrypt credentials function intended to be used with `cemerick.friend/authenticate`
   or individual authentication workflows.  You must supply a function of one argument
   that will look up stored user credentials given a username/id.  e.g.:

   (authenticate {:credential-fn (partial bcrypt-credential-fn load-user-record)
                  :other :config ...}
     ring-handler-to-be-secured)"
  [load-credentials-fn {:keys [username password]}]
  (when-let [creds (load-credentials-fn username)]
    (when (BCrypt/checkpw password (:password creds))
      (dissoc creds :password))))