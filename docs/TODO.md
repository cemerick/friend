## TODO

* run-as/sudo/multi-user login
* alternative hashing methods and salting strategies
  * good to encourage bcrypt, but existing apps have tons of sha-X, md5,
    etc passwords
* remember-me?
* fine-grained authorization (viz. ACLs, etc)
  * maybe something compelling can fall out of existing treatment of
    roles?
* interop
  * recognize / provide access to servlet principal
  * spring-security
* make `:cemerick.friend/workflow` metadata
* documentation
  * authentication retention
  * authentication map metadata:
    * `:type`
    * `::friend/workflow`
    * `::friend/redirect-on-auth?`