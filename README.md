# Github User App

This is a simple app which pulls from the Github /users and /repos endpoints
and marries together parts of the data

## Description

A few interesting challenges posed themselves with this application

* The Github API allows for querying from the /users/{username} and /users/{username}/repos endpoints
* When using an unauthenticated user, the rate limit is very small ( about 60 calls )
* In order to circumvent this scenario, outside of creating a Github App for an authenticated user, a caching mechanism was put into place via utilizing Caffeine and caching on the username as the key
* The Github API is also prone to throwing 429 errors for constant pings, so that was considered when making the calls to the API
* Since two calls needed to made, an approach was decided that the calls would be made in parallel to maximize time and resources
* While not directly specified, it was my decision to allow for partial success since it would save a successful call even if one failed
* Retry via spring-retry was added to both calls in case of rate limiting, but service errors, 404s and bad requests would not be retried as that could lead to retry exhaustion or potential rate limit waste
* Wiremock was used in integration testing and several components were unit tested in isolation to verify operations and orchestration working as intended
* Testing some of the components, e.g. async, was a challenge, and the tests do the best to reflect the intention
* The application controller tries to save some unnecessary processing by blocking requests that do not comply with the basic requirements for a Github username
* A Global Exception Handler was used to try to standardize errors coming from the platform and downstream service

### Executing program

In order to run the application, there are 3 main ways:
1. If you have IntelliJ, you can simply run it as you would any Spring Boot app by clicking on the run arrow
2. From the terminal, inside of the project, you can run  ```./gradlew bootRun```
3. If you have Docker installed, you can run the following commands as a Dockerfile is present in the app:
```
./gradlew clean build
docker build -t tevore/my-spring-boot-app .
docker run -p 8080:8080 tevore/my-spring-boot-app
```