Remember-me token with Clojure

# Reminder about remember-me cookie
[The best way to implement a remember-me feature](http://stackoverflow.com/questions/244882/what-is-the-best-way-to-implement-remember-me-for-a-website ).

In short, given a login id (a string of whatever login id you want), the remember-me library issue a serie and a token.

[Token should not be stored as-is](http://stackoverflow.com/questions/549/the-definitive-guide-to-form-based-website-authentication#477579), as a database leak would means a way for attacker to log in the accounts. The hash use a SHA3 digest ([because SHA1 should not be used anymore](https://konklone.com/post/why-google-is-hurrying-the-web-to-kill-sha-1)) and use [Pandect clojure library](https://github.com/xsc/pandect).

The persistence functions are defined through a protocol and a Datomic implementation is provided, you can also implement you own and inject it when using the functionality.

Finally, a persistent cookie (not a session one) is issued.


# Friend implementation

## Issueing a remember-me Cookie at login

* The `interactive-form` workflow retrieve the remember-me form parameters. 
* The workflow function verifies the supplied credentials (username/password) with the ```bcrypt-credential-fn``` (through the login config), 
	* if the form parameters ```remember-me``` is set to "true" then 
		* the ```bcrypt-credential-fn``` invoke the ```credentials/remember-me``` function that issue new remember-me data (not the cookie yet) that will be returned through the authenticate response. 
		* The ```remember-me``` function is given a ```save-remember-me-fn!``` as a first parameters to allow the persistent storage of the issued data. The ```save-remember-me-fn!``` is defined with the login config. 
* If any remember-me data is present in the interactive-login workflow response then it is encoded into a cookie in the ```friend/authenticate*``` function with the ```friend/set-cookies-if-any``` function.

## Authenticate with a remember-me cookie

Once issued and sent to the client, each subsequent http request will include the persistent remember-me cookie.

* The ```workflow/remember-me-hash``` function workflow test if a remember-me cookie is present in the request.
	* It verifies the validity of the cookie with the ```credentials/remember-me-hash-fn``` that loads the stored remember-me data and then compare with the data provided in the cookie (validity, expiration, etc.), otherwise it returns nil and the ```make-auth``` fn does not make it.
	* The ```workflow/remember-me-hash``` then ```make-auth``` and transmit the authenticated request to the subsequent handler



