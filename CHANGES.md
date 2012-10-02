## [Friend](http://github.com/cemerick/friend) changelog

### `0.1.2`

**Core API**

* Credential functions may now return maps with a `:cemerick.friend.credentials/password-key`
slot in their metadata to indicate the key within the credential map itself which holds the
password.
* The value of the `:cemerick.friend/redirect-on-auth?` key in workflow may now be a string
URI to which the user will be redirected (instead of the `:default-landing-uri` provided to
the `authenticate` middleware).
* Friend now plays much nicer with Ring sessions; in particular, it no longer quashes
session data set by lower-level handlers and middleware.  (gh-24, gh-26)

**Workflows**

* The `http-basic` workflow now properly supports empty usernames and passwords (gh-28)

### `0.1.1`

Bricked.

### `0.1.0`

**Core API**

* `:login-uri` now actually defaults to `"/login"` as indicated in documentation (Yoshito Komatsu, gh-13)
* Authorization failures are now handled more sanely (gh-19):
  * `:unauthorized-redirect-uri` is no longer used (was nonsensical)
  * additional data may now be added to the stone thrown upon unauthorized access (see `cemerick.friend/authorize`, `cemerick.friend/authenticated`, and `throw-authorized`)
  * data added to stone thrown by `cemerick.friend/throw-authorized` is now added to the request passed to `:unauthorized-handler` in the `:cemerick.friend/authorization-failure` slot
* HTTP 401 is now used instead of 403 to properly indicate unauthorized authenticated request (gh-20)
* `cemerick.friend/logout*` is now public (John Szakmeister)

**Workflows**

* http-basic workflow now properly responds with www-authenicate challenge when no credentials are provided and `:allow-anon?` is false (gh-16)
* the OpenID workflow's `:max-nonce-age` must now be specified in milliseconds instead of seconds
* the OpenID workflow no longer adds unprintable objects to the ring session

**Misc**

* Documentation for `cemerick.friend/identity` fixed

