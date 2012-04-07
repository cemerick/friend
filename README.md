# Friend

An extensible authentication and authorization library for
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

## Overview

Friend is intended to provide a foundation for addressing
all of the security concerns associated with web apps:

* channel security (restricting certain resources to a particular
  protocol/scheme, usually HTTPS)
* user agent authentication; Friend currently includes support for form,
  HTTP Basic, and OpenId authentication, and makes it easy to:
  * implement and use other workflows
  * integrate app-specific user-credential checks
* role-based authentication
  * optionally uses Clojure's ad-hoc hierarchies to model hierarchical
    roles
* `su` capabilities (a.k.a. "log in as"), enabling administrators to
  easily take on user identities for debugging or support purposes (**in
progress**)
* and the creature comforts:
  * Ring middlewares for configuring and defining the scopes of
    authentication, authorization, and channel security
  * Macros to clearly demarcate the scope of authentication and
    authorization within code that is "below" the level of Ring handlers
    where you can't use middlewares.
  * A reasonable Clojure API around the jbcrypt library for hashing
    sensitive bits.
  * Enables DRY routes and configuration, e.g. no need to configure your
    routes in Compojure or Noir or Moustache, and separately specify
    which routes fall under the jurisdiction of Friend's security machinery
  * Purely functional in nature: authentications, roles, and session
    data are obtained, retained, and passed around as good ol'
    persistent data structures (just as Ring intended).  No stateful session
    or context is ever bashed in place, making it easier to reason about
    what's going on.

### Why?

Nothing like Friend exists, and it needs to.  Securing Ring applications
and services is (charitably speaking) a PITA right now, with everyone
rolling their own, or starting with relatively low-level middlewares and
frameworks.  This will never do.  Serious web applications need to take
security seriously, and need to readily interoperate with all sorts of
authentication mechanisms that have come to litter the web as well as
internal networks.

Friend has been built with one eye on a number of frameworks.

* [warden](https://github.com/hassox/warden/wiki)
* [spring-security](http://static.springsource.org/spring-security/)
* [everyauth](https://github.com/bnoguchi/everyauth)
* [omniauth](https://github.com/intridea/omniauth)
* [sandbar](https://github.com/brentonashworth/sandbar)

### Status

Friend is brand-spanking-new.  It's also obviously involved in security
matters.  While it's hardly untested, and _is_ in use in production,
it's obviously not seen the kind of beating and vetting that established
security libraries and frameworks have had (i.e. spring-security, JAAS
stuff, etc).

So, proceed happily, but mindfully.  Only with your help will we have a
widely-tested Ring application security library.

### Known issues

* Configuration keys need a bit of tidying, especially for those that
  can/should apply to multiple authorization workflows.  Fixes for such
things will break the existing API.
* the `su` mechanism is in-progress
* the OpenId authentication workflow needs to be broken out into a
  separate project so that those who aren't using it don't suffer its
transitive dependencies.  (The form and HTTP Basic workflows are
dependency-free, and will likely remain here.)
* …surely there's more.  File issues.

## "Installation"

Friend is available in Clojars. Add this `:dependency` to your Leiningen
`project.clj`:

```clojure
[com.cemerick/friend "0.0.1"]
```

Or, add this to your Maven project's `pom.xml`:

```xml
<repository>
  <id>clojars</id>
  <url>http://clojars.org/repo</url>
</repository>

<dependency>
  <groupId>com.cemerick</groupId>
  <artifactId>friend</artifactId>
  <version>0.0.1</version>
</dependency>
```

Friend is compatible with Clojure 1.2.0 - 1.4.0.

## Usage

There is a fairly ornate Ring application
[here](friend/blob/master/test/test_friend/mock_app.clj) that is the basis for Friend's
functional tests that you can look at.  That's likely a little hard to
navigate though, so a simpler introduction is worthwhile.

Here's probably the most self-contained Friend usage possible:  

```clojure
(ns your.ring.app
  (:require [cemerick.friend :as friend]
            (cemerick.friend [workflows :as workflows]
                             [credentials :as creds])))

; a dummy in-memory user "database"
(def users {"root" {:username "root"
                    :password (creds/hash-bcrypt "admin_password")
                    :roles #{::admin}}
            "jane" {:username "jane"
                    :password (creds/hash-bcrypt "user_password")
                    :roles #{::user}}})

(def ring-app ; ... assemble routes however you like ...
  )

(def secured-app
  (->> ring-app
    (friend/authenticate {:credential-fn (partial creds/bcrypt-credential-fn users)
                          :workflows [(workflows/interactive-form)]})
    ; ...required Ring middlewares ...
    ))
```

We have an unadorned (and unsecured) Ring application (`ring-app`, which
can be any Ring handler), and then the usage of Friend's `authenticate`
middleware.  This is where all of the authentication work will be done,
with the return value being a secured Ring application (`secured-app`),
the requests to which are subject to the configuration provided to
`authenticate` and the authorization contexts that are defined within
`ring-app` (which we'll get to shortly).

There are two key abstractions employed during authentication: workflows
and credential functions.

(Note that Friend itself requires some core Ring middlewares: `params`,
`keyword-params` and `nested-params`.  Most workflows will additionally
require `session` in order to support post-authentication redirection to
previously-unauthorized resources, retention of tokens and nonces for
workflows like OpenId and oAuth, etc.  HTTP Basic is the only provided
workflow that does not require `session` middleware.)

### Workflows

Individual authentication methods (e.g., form-based auth, HTTP Basic, OpenID,
oAuth, etc.) are implemented as _workflows_ in Friend.  A workflow is a
regular Ring handler function, except that a workflow function can _opt_
to return an _authentication map_ instead of a Ring response if a
request is authenticated.  A diagram may help:

![](https://github.com/cemerick/friend/raw/master/docs/workflow.png)

You can define any number of workflows in a `:workflows` kwarg to
`authenticate`.  Incoming requests are always run through the configured
workflows prior to potentially being passed along to the secured Ring
application.

If a workflow returns an authentication map, then the `authenticate`
middleware will either:

* carry on processing the request if the workflow allows for credentials
  to be provided in requests to any resource (i.e. HTTP Basic); control
  of this is entirely up to each workflow, and will be described later.
* redirect the user agent to a secured resource that it was previously
  barred from accessing via Friend's authorization machinery

If a workflow returns a Ring response, then that response is sent back
to the user agent straight away (after some bookkeeping by the
`authenticate` middleware to preserve session states and such).  This 
makes it possible for a workflow to control a "local" dataflow between
itself, the user agent, and any necessary external authorities (e.g. by
redirecting a user agent to an OpenId endpoint, performing token
exchange in the case of oAuth, etc., eventually returning a complete
authentication map that will allow the user agent to proceed on its
desired vector).

### Credential functions and authentication maps

Workflows use a _credential function_ to verify the credentials provided
to them via requests.  Credential functions can be specified either as a
`:credential-fn` option to `cemerick.friend/authenticate`, or often as
an (overriding) `:credential-fn` option to individual workflow
functions.

All credential functions take a single argument, a map containing the
available credentials that additionally contains a
`:cemerick.friend/workflow` slot identifying which workflow has produced
the credential.  For example, the default form-based authentication
credential map looks like this:

```clojure
{:username "...", :password "...", :cemerick.friend/workflow :form}
```

HTTP Basic credentials are much the same, but with a workflow value of
`:http-basic`, etc.  Different workflows may have significantly different
credential maps (e.g. an OpenID workflow does not provide username and
password, but rather a token returned by an OpenID provider along with
potentially some number of "attributes" like the user's name, email
address, default language, etc.), and unique credential verification
requirements (again, contrast the simple username/password verification
of form or HTTP Basic credentials and OpenId, which, in
general, when presented with unknown credentials, should _register_ the
indicated identity rather than verifying it).

In summary, the contract of what exactly must be in the map provided to
credential functions is entirely at the discretion of each workflow
function, as is the semantics of the credential function.

If a map of credentials is verified by a credential function, it should return a
_authentication map_ that aggregates all authentication and authorization
information available for the identified user.  This map may contain many
entries, depending upon the authentication information that is relevant for the
workflow in question and the user data relevant to the application, but two entries are priviliged:

* `:identity` (**required**) corresponds with e.g. the username in a form or
  HTTP Basic authentication, an oAuth token, etc.; this value _must_ be
  unique across all users within the application
* `:roles`, an optional collection of values enumerating the roles for which the user
  is authorized.

_If a map of credentials is found to be invalid, the credential function must
return nil._

### Authentication retention (or not)

### Authorization

#### Simple vs. hierarchical roles

### Channel security

_Channel security_ is the redirection of requests for a given resource
through a specific channel, i.e. requiring that logins or a payment
workflow is performed over HTTPS instead over HTTP.

`requires-scheme` is Ring middleware that enforces channel security for
a given Ring handler:

```clojure
(use '[cemerick.friend :only (requires-scheme *default-scheme-ports*)])

; HTTP requests routed to https-routes will be redirected to the
; corresponding HTTPS URL on the default port
(def https-routes (requires-scheme routes :https))

; HTTP requests routed to custom-https-port-routes be redirected to the
; corresponding HTTPS URL on port 8443
(def custom-https-port-routes (requires-scheme routes :https {:https 8443}))

; alternative default ports for HTTP and HTTPS may be bound dynamically
; to simplify configuration of multiple routes
(binding [*default-scheme-ports* {:http 8080 :https 8443}]
  (def http-routes (requires-scheme routes :http))
  (def https-routes (requires-scheme routes :https)))
```

Note that `requires-scheme` is unrelated to the authentication,
authorization, etc facilities in Friend, and can be used in isolation. 

## TODO

* run-as/sudo/multi-user login
* alternative hashing methods and salting strategies
  * good to encourage bcrypt, but existing apps have tons of sha-X, md5,
    etc passwords
* remember-me?
* interop
  * recognize / provide access to servlet principal
  * spring-security
* make `:cemerick.friend/workflow` metadata
* documentation
  * authentication map metadata:
    * `:type`
    * `::friend/workflow`
    * `::friend/transient`
    * `::friend/redirect-on-auth?`

## Need Help?

Ping `cemerick` on freenode irc or
[twitter](http://twitter.com/cemerick) if you have questions or would
like to contribute patches.

## License

Copyright ©2012 [Chas Emerick](http://cemerick.com)

Distributed under the Eclipse Public License, the same as Clojure.
Please see the `epl-v10.html` file at the top level of this repo.
