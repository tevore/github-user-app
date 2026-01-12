package com.example.demo.service.integration;

import com.example.demo.domain.GithubUser;
import com.example.demo.service.GithubService;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@EnableCaching
@EnableWireMock(@ConfigureWireMock(port = 8081))
public class GithubServiceIntegrationTest {

    @Autowired
    GithubService githubService;

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
    public void shouldFindUserAndValidateCacheHitForSameCall() {

        //Verify cache is empty
        Cache githubUserCache = cacheManager.getCache("githubUsers");
        assertNull(githubUserCache.get("some-user"));

        stubFor(get(urlEqualTo("/users/some-user"))
                .willReturn(okJson("{\"login\":\"some-user\"}")));

        //Two calls to ensure the cache is being hit
        GithubUser firstCall = githubService.getGithubUserInfo("some-user");

        GithubUser secondCall = githubService.getGithubUserInfo("some-user");

        assertEquals(firstCall.name(), secondCall.name());

        //Verify cache has user we just searched for
        GithubUser cachedUser = githubUserCache.get("some-user", GithubUser.class);
        assertNotNull(cachedUser);
        assertEquals("some-user", cachedUser.login());

        //Since we hit the cache, wiremock should have a single count
        wireMockServer.verify(1, getRequestedFor(urlEqualTo("/users/some-user")));
    }

    @Test
    public void shouldVerifyCacheEviction() throws InterruptedException {

        //Verify cache is empty
        Cache githubUserCache = cacheManager.getCache("githubUsers");
        assertNull(githubUserCache.get("some-user"));

        stubFor(get(urlEqualTo("/users/some-user"))
                .willReturn(okJson("{\"login\":\"some-user\"}")));

        //Two calls to ensure the cache is being hit
        GithubUser firstCall = githubService.getGithubUserInfo("some-user");

        Thread.sleep(6000);

        GithubUser secondCall = githubService.getGithubUserInfo("some-user");

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
                githubService.getGithubUserInfo("nonexistent-user"));

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
                githubService.getGithubUserInfo("some-user"));

        wireMockServer.verify(getRequestedFor(urlEqualTo("/users/some-user")));
    }

    @Test
    public void shouldFailDueToNullUsername() {

        stubFor(get(urlEqualTo("/users/"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Internal Server Error\"}")));

        assertThrows(HttpClientErrorException.class, () ->
                githubService.getGithubUserInfo(null));

    }
}
