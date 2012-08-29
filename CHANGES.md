## [Friend](http://github.com/cemerick/friend) changelog

### `0.1.0`

*Core API*

* `:login-uri` now actually defaults to `"/login"` as indicated in documentation (Yoshito Komatsu, gh-13)
* Authorization failures are now handled more sanely (gh-19):
** `:unauthorized-redirect-uri` is no longer used (was nonsensical)
** additional data may now be added to the stone thrown upon unauthorized access (see `cemerick.friend/authorize`, `cemerick.friend/authenticated`, and `throw-authorized`)
** data added to stone thrown by `cemerick.friend/throw-authorized` is now added to the request passed to `:unauthorized-handler` in the `:cemerick.friend/authorization-failure` slot
* HTTP 401 is now used instead of 403 to properly indicate unauthorized authenticated request (gh-20)
* `cemerick.friend/logout*` is now public (John Szakmeister)

*Workflows*

* http-basic workflow now properly responds with www-authenicate challenge when no credentials are provided and `:allow-anon?` is false (gh-16)
* the OpenID workflow's `:max-nonce-age` must now be specified in milliseconds instead of seconds
* the OpenID workflow no longer adds unprintable objects to the ring session

*Misc*

* Documentation for `cemerick.friend/identity` fixed

