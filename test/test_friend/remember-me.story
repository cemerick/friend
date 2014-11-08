Narrative:
As a user
I want to login
In order to access to protected resource

Scenario: successful user creation and authentication with remember-me worflow and remember-me checkbox unchecked
Given a running webapp
When the user creates its account {:username "jane" :password "user_password"}
When the user access the protected resource with url '/user/account?query-string=test'
Then the body response is the login page
When the user login: http POST to "/login" URL with previous form params
Then the user should be authenticated: http response 303 and location is

Scenario: failed authentication with remember-me workflow and no remember-me checkbox unchecked
Given a running webapp and a user storage containing {:username "jane" :password "user_password"} and no remember-me token
When the user login: http POST to "/login" URL with previous form params
Then the user should not be authenticated: http response 403 and login page still displayed with error message

Scenario: successful authentication with remember-me worflow and remember-me checkbox checked
Given a running webapp and a user storage containing {:username "jane" :password "user_password"} and no remember-me token
When the user login: http POST to "/login" URL with previous form params
Then the user should be authenticated: http response 200 and welcome page is displayed
Then the http response should contain a cookie named "remember-me"

Scenario: successful authentication with remember-me token
Given the user session expires on the server
When the user perform an http request on a protected resource (welcome page) with the token cookie
Then the user should be authenticated: http response 200 and welcome page displayed
