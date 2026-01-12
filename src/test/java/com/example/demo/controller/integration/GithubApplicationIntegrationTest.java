package com.example.demo.controller.integration;

import com.example.demo.controller.GithubController;
import com.example.demo.domain.GithubUser;
import com.example.demo.utils.TestUtils;
import com.github.tomakehurst.wiremock.WireMockServer;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;

import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@EnableCaching
@EnableWireMock(@ConfigureWireMock(port = 8081))
public class GithubApplicationIntegrationTest {

    @Autowired
    GithubController githubController;

    @Autowired
    private CacheManager cacheManager;

    @Value("${wiremock.server.baseUrl}")
    private String wireMockUrl;

    @InjectWireMock
    private WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        cacheManager.resetCaches();
    }

    @Test
    public void shouldSuccessfullyReturnAUser() {

        //Verify cache is empty
        Cache githubUserCache = cacheManager.getCache("githubUsers");
        assertNull(githubUserCache.get("some-user"));

        stubFor(get(urlEqualTo("/users/some-user"))
                .willReturn(okJson("{\"login\":\"some-user\"}")));

        //Two calls to ensure the cache is being hit
        GithubUser firstCall = githubController.retrieveGithubUser("some-user");

        GithubUser secondCall = githubController.retrieveGithubUser("some-user");

        assertEquals(firstCall.name(), secondCall.name());

        //Verify cache has user we just searched for
        GithubUser cachedUser = githubUserCache.get("some-user", GithubUser.class);
        assertNotNull(cachedUser);
        assertEquals("some-user", cachedUser.login());

        //Since we hit the cache, wiremock should have a single count
        wireMockServer.verify(1, getRequestedFor(urlEqualTo("/users/some-user")));
    }

    @Test
    public void shouldVerifyCacheEvictionOnSuccessfulRetrieval() throws InterruptedException {
        //Verify cache is empty
        Cache githubUserCache = cacheManager.getCache("githubUsers");
        assertNull(githubUserCache.get("some-user"));

        stubFor(get(urlEqualTo("/users/some-user"))
                .willReturn(okJson("{\"login\":\"some-user\"}")));

        //Two calls to ensure the cache is being hit
        GithubUser firstCall = githubController.retrieveGithubUser("some-user");

        Thread.sleep(6000);

        GithubUser secondCall = githubController.retrieveGithubUser("some-user");

        assertEquals(firstCall.name(), secondCall.name());

        //Verify cache has user we just searched for
        GithubUser cachedUser = githubUserCache.get("some-user", GithubUser.class);
        assertNotNull(cachedUser);
        assertEquals("some-user", cachedUser.login());

        //Since we let the cache expire, wiremock should have 2 hit counts
        wireMockServer.verify(2, getRequestedFor(urlEqualTo("/users/some-user")));
    }

    @Test
    public void shouldNotFindUser() {

        stubFor(get(urlEqualTo("/users/nonexistent-user"))
                .willReturn(aResponse()
                        .withBody(("{\"message\":\"User not found\"}"))
                        .withHeader("Content-Type", "application/json")
                        .withStatus(404)));

        assertThrows(HttpClientErrorException.class, () ->
                githubController.retrieveGithubUser("nonexistent-user"));

        // Verify WireMock received the request
        wireMockServer.verify(getRequestedFor(urlEqualTo("/users/nonexistent-user")));

    }

    @Test
    public void shouldFailDueToServiceError() {

        stubFor(get(urlEqualTo("/users/some-user"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Internal Server Error\"}")));

        assertThrows(HttpServerErrorException.class, () ->
                githubController.retrieveGithubUser("some-user"));

        wireMockServer.verify(getRequestedFor(urlEqualTo("/users/some-user")));
    }

    @Test
    void shouldThrowErrorMessageDueToMissingUsernameValue() {

        stubFor(get(urlEqualTo("/users/"))
                .willReturn(aResponse()
                        .withBody(("{\"message\":\"User not found\"}"))
                        .withHeader("Content-Type", "application/json")
                        .withStatus(404)));

        assertThrows(HttpClientErrorException.class, () ->
                githubController.retrieveGithubUser(null));
    }

    @Test
    void shouldThrowErrorMessageDueToInvalidUsername() {

        Set<ConstraintViolation<?>> constraintsViolations = assertThrows(ConstraintViolationException.class, () -> {
            githubController.retrieveGithubUser("--bad-user");
        }).getConstraintViolations();

        constraintsViolations.forEach(cv -> {
            assertEquals(TestUtils.INVALID_FORMAT_USERNAME, cv.getMessage());
        });


    }

    @Test
    void shouldThrowErrorMessageDueToUsernameBeingTooLong() {

        Set<ConstraintViolation<?>> constraintsViolations = assertThrows(ConstraintViolationException.class, () -> {
            githubController.retrieveGithubUser("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        }).getConstraintViolations();

        constraintsViolations.forEach(cv -> {
            assertEquals(TestUtils.INVALID_LENGTH_USERNAME, cv.getMessage());
        });

    }


}
