## [Friend](http://github.com/cemerick/friend) changelog

### `0.2.0`

This release contains a significant refactoring of the library to follow the
interceptor pattern adopted by Ring 1.2.0 (see the note under "Misc" below).

Also, Friend now depends upon Ring 1.2.0 final.

**Core API**

* The `default-unauthenticated-handler` now properly retains the query string of
  the initial requested unauthenticated URL (gh-68)

**Workflows**

* The OpenID workflow can now be used much more reliably behind reverse proxies
  and load balancers:
  * The `return_to` URL now automatically takes into account any
    `x-forwarded-proto` header provided by your reverse proxy
  * If your proxy/load balancer doesn't send `x-forwarded-proto` headers, then
    you can use middleware to add an appropriate `return_to` URL to the request
    going into the OpenID middleware, keyed under
    `:cemerick.friend.openid/return-url` (gh-74)
* The interactive-form workflow now properly picks up `username` parameter value
  after a failed login attempt (gh-69)

**Misc**

* Friend's middlewares have been refactored internally to implement an
  interceptor pattern, to match Ring's middlewares >= 1.2.0.  This makes Friend
  suitable for use with e.g. Pedestal and similar frameworks. (gh-54)

### `0.1.5`

Friend is now tracking Ring v1.2.0 betas, minimally requiring
`[ring/ring-core "1.2.0-beta1"]`.

**Core API**

* `:roles` in authentication maps may now optionally be a function returning a
  collection of roles (gh-21, gh-55)

**Workflows**

* All included workflows now properly account for the in-force Ring context, if
  any. (gh-52, gh-53, gh-56)

### `0.1.4`

**Core API**

* Fixed handling of the optional authorization-error map that may be provided 
  to `authorize` (gh-46)

**Misc**

* Various minor documentation improvements.

### `0.1.3`

**Core API**

* `cemerick.friend/current-authentication` can now accept either a ring request
  map or a Friend identity map
* `cemerick.friend/authenticated` can now accept more than one body form
  (gh-32)
* A new `cemerick.friend/authenticate` option, `:unauthenticated-handler`,
  allows one to provide a separate Ring handler to control how to respond to
unauthenticated requests when authentication is required (either via setting
`:allow-anon` to `false`, or via use of `cemerick.friend/authenticated`). The
prior behaviour (redirecting to the URI specified by `:login-uri`) is currently
retained by the default `:unauthenticated-handler`,
`cemerick.friend/default-unauthenticated-handler`. (gh-38)

**Workflows**

* The `http-basic` workflow no longer produces a 401 Unauthorized response when
  no HTTP Basic credentials are supplied. (gh-38)
* The OpenID workflow now offers a `:consumer-manager` option for providing a
  fully-configured `org.openid4java.consumer.ConsumerManager` (to be used
instead of the in-memory default) (gh-35)
* Usernames provided as part of an interactive-form workflow authentication are
  now URL-encoded in the resulting redirect when authentication fails (gh-41)

**Misc**

* New function `cemerick.friend.credentials/bcrypt-verify` now available to
  verify bcrypt-hashed strings outside of
`cemerick.friend.credentials/bcrypt-credential-fn` and the
workflow/authentication process
* All HTTP redirect responses sent by Friend now use an absolute URL in the
  `Location` header per the HTTP spec (gh-42)
* The transitive dependency on Google Guice (needed by the openid4java
  dependency) has been updated to use the coordinates available via Maven
Central 

### `0.1.2`

**Core API**

* Credential functions may now return maps with a
  `:cemerick.friend.credentials/password-key` slot in their metadata to
indicate the key within the credential map itself which holds the password.
* The value of the `:cemerick.friend/redirect-on-auth?` key in workflow may now
  be a string URI to which the user will be redirected (instead of the
`:default-landing-uri` provided to the `authenticate` middleware).
* Friend now plays much nicer with Ring sessions; in particular, it no longer
  quashes session data set by lower-level handlers and middleware.  (gh-24,
gh-26)

**Workflows**

* The `http-basic` workflow now properly supports empty usernames and passwords
  (gh-28)

### `0.1.1`

Bricked, don't use.

### `0.1.0`

**Core API**

* `:login-uri` now actually defaults to `"/login"` as indicated in
  documentation (Yoshito Komatsu, gh-13)
* Authorization failures are now handled more sanely (gh-19):
  * `:unauthorized-redirect-uri` is no longer used (was nonsensical)
  * additional data may now be added to the stone thrown upon unauthorized
    access (see `cemerick.friend/authorize`, `cemerick.friend/authenticated`,
and `throw-authorized`)
  * data added to stone thrown by `cemerick.friend/throw-authorized` is now
    added to the request passed to `:unauthorized-handler` in the
`:cemerick.friend/authorization-failure` slot
* HTTP 401 is now used instead of 403 to properly indicate unauthorized
  authenticated request (gh-20)
* `cemerick.friend/logout*` is now public (John Szakmeister)

**Workflows**

* http-basic workflow now properly responds with www-authenticate challenge when
  no credentials are provided and `:allow-anon?` is false (gh-16)
* the OpenID workflow's `:max-nonce-age` must now be specified in milliseconds
  instead of seconds
* the OpenID workflow no longer adds unprintable objects to the ring session

**Misc**

* Documentation for `cemerick.friend/identity` fixed

