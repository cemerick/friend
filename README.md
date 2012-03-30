# Friend

An authentication and authorization library for
[Clojure](http://clojure.org) [Ring](http://github.com/mmcgrana/ring)
applications and services.

```
Picking up his staff he stood before the rock and said in a clear voice:
Mellon!

The star shone out briefly and faded again. Then silently a great
doorway was outlined, though not a crack or joint had been visible
before. Slowly it divided in the middle and swung outwards inch by inch,
until both doors lay back against the wall. Through the opening a
shadowy stair could be seen climbing steeply up; but beyond the lower
steps the darkness was deeper than the night. The Company stared in
wonder.

"I was wrong after all," said Gandalf, "and Gimli too. Merry, of all
people, was on the right track. The opening word was inscribed on the
archway all the time! The translation should have been: Say 'Friend' and
enter. I had only to speak the Elvish work for friend and the doors
opened. Quite simple. Too simple for a learned lore master in these
suspicious days. Those were happier times. Now let us go!" 
```
— J.R.R. Tolkien, _Lord of the Rings_ 

## "Installation"

## Usage

### Features

#### Channel security

```clojure
(use '[cemerick.friend :only (requires-scheme *default-scheme-ports*)])

(def https-routes (requires-scheme routes :https))

(def http-routes (requires-scheme routes :http))

(def custom-https-port-routes (requires-scheme routes :https {:https 8443}))

(binding [*default-scheme-ports* {:http 8080 :https 8443}]
  (def http-routes (requires-scheme routes :http))
  (def https-routes (requires-scheme routes :https)))
```

#### Credential functions

Workflows use a credential function to verify the credentials provided to them.
Credential functions can be specified either as a `:credential-fn` option to
`cemerick.friend/authenticate`, or often as (an overriding) `:credential-fn`
option to individual workflow functions.

All credential functions take a single argument, a map containing the available
credentials, and hopefully a `:cemerick.friend/workflow` slot identifying which
workflow has produced the credential.  For example, the default form-based
authentication credential map looks like this:

```clojure
{:username "…" :password "…" :cemerick.friend/workflow :form}
```

HTTP Basic credentials are much the same, but with a workflow value of
`:http-basic`, etc.  Different workflows may have significantly different
credential maps (e.g. an OpenID workflow would not provide username and
password, but rather a token returned by an OpenID provider).

If a map of credentials is verified by a credential function, it should return a
_authentication map_ that aggregates all authentication and authorization
information available for the identified user.  This map may contain many
entries, depending upon the authentication information that is relevant for the
workflow in question and the user data relevant to the application:

* `:identity` (**required**) corresponds with e.g. the username in a form or
  HTTP Basic authentication, an oAuth token, etc.
* `:roles`, an optional set of values enumerating the roles for which the user
  is authorized.

If a map of credentials is found to be invalid, the credential function must
return nil.

#### Authentication retention (or not)



#### Logout

Logging a user out is accomplished by directing the user to a route that
has had `cemerick.friend/logout` middleware applied to it:

```clojure
(use '[cemerick.friend :only (logout)])
(def logout-route (logout logout-handler))
```


`:cemerick.friend/auth` entry from their session.  You can do this manually, or
use the `logout` middleware to ensure that that entry 


#### Workflows

Individual authentication methods (e.g., form-based auth, HTTP Basic, OpenID,
oAuth, etc.) are implemented as _workflows_ in Friend.  A workflow is a regular
Ring handler function, except that, rather than potentially returning a Ring
response, a workflow function can opt to return an authentication map if a
request has been authenticated fully.

* salts
* crypts/ciphers
* remember-me
* role-based authentication
* run-as/sudo/multi-user login
* interop
  * recognize / provide access to servlet principal
  * spring-security

## TODO

* use hierarchies and `isa?` instead of sets of simple values for
  authorization

## Need Help?

Ping `cemerick` on freenode irc or
[twitter](http://twitter.com/cemerick) if you have questions or would
like to contribute patches.

## License

Copyright ©2012 [Chas Emerick](http://cemerick.com)

Distributed under the Eclipse Public License, the same as Clojure.
