# Friend  [![Travis CI status](https://secure.travis-ci.org/cemerick/friend.png)](http://travis-ci.org/#!/cemerick/friend/builds)

An extensible authentication and authorization library for
[Clojure](http://clojure.org)/[Ring](http://github.com/ring-clojure/ring)
web applications and services.

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
enter. I had only to speak the Elvish word for friend and the doors
opened. Quite simple. Too simple for a learned lore master in these
suspicious days. Those were happier times. Now let us go!"
```
— J.R.R. Tolkien, _Lord of the Rings_

## Overview

Friend is intended to provide a foundation for addressing
all of the authentication and authorization concerns associated with web
apps:

* user agent authentication; Friend currently includes support for form,
  HTTP Basic, and OpenId authentication, and makes it easy to:
  * implement and use other workflows
  * integrate app-specific user-credential checks
* role-based authorization
  * optionally uses Clojure's ad-hoc hierarchies to model hierarchical
    roles
* `su` capabilities (a.k.a. "log in as"), enabling users to maintain
  multiple simultaneous logins, as well as to allow administrators to
  take on users' identities for debugging or support purposes (**in
      progress**)
* channel security (restricting certain resources to a particular
  protocol/scheme, usually HTTPS)
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
[com.cemerick/friend "0.2.0"]
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
  <version>0.2.0</version>
</dependency>
```

Friend is compatible with Clojure 1.2.0 - 1.5.0+.

## Documentation

You can find documentation in the [docs](http://github.com/cemerick/friend/blob/master/docs) folder.

## Changelog

Available [here](http://github.com/cemerick/friend/blob/master/CHANGES.md).

## Need Help?

Ping `cemerick` on freenode irc or
[twitter](http://twitter.com/cemerick) if you have questions or would
like to contribute patches.

## License

Copyright ©2012-2013 [Chas Emerick](http://cemerick.com) and other contributors.

Distributed under the Eclipse Public License, the same as Clojure.
Please see the `epl-v10.html` file at the top level of this repo.
