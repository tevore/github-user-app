package com.tevore.controller.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.tevore.configuration.TestCacheConfig;
import com.tevore.controller.GithubController;
import com.tevore.domain.GithubRepo;
import com.tevore.domain.GithubUser;
import com.tevore.domain.GithubUserWithReposResponse;
import com.tevore.utils.TestUtils;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EnableWireMock(@ConfigureWireMock(port = 0)) // dynamic port avoids collisions
@Import(TestCacheConfig.class)
@ActiveProfiles("test")
public class GithubApplicationIntegrationTest {

    @Autowired
    GithubController githubController;

    @Autowired
    CacheManager cacheManager;

    @InjectWireMock
    WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();

        Cache users = cacheManager.getCache("githubUsers");
        Cache repos = cacheManager.getCache("githubUserRepos");
        assertNotNull(users, "githubUsers cache must exist");
        assertNotNull(repos, "githubUserRepos cache must exist");

        users.clear();
        repos.clear();
    }

    @Test
    public void shouldSuccessfullyReturnAUserAndCacheBothResults() {
        // Verify caches are empty
        Cache githubUserCache = cacheManager.getCache("githubUsers");
        Cache githubRepoCache = cacheManager.getCache("githubUserRepos");
        assertNull(githubUserCache.get("some-user"));
        assertNull(githubRepoCache.get("some-user"));

        stubFor(get(urlEqualTo("/users/some-user"))
                .willReturn(okJson("{\"login\":\"some-user\"}")));

        stubFor(get(urlEqualTo("/users/some-user/repos"))
                .willReturn(okJson("[{\"name\":\"repo\",\"url\":\"example.com\"}]")));

        // Two calls to ensure the cache is being hit
        GithubUserWithReposResponse firstCall = githubController.retrieveGithubUser("some-user");
        GithubUserWithReposResponse secondCall = githubController.retrieveGithubUser("some-user");

        // Basic response assertions
        assertEquals(firstCall.login(), secondCall.login());
        assertEquals(1, secondCall.repos().size());
        assertEquals("repo", secondCall.repos().get(0).name());

        // Verify cache has user
        GithubUser cachedUser = githubUserCache.get("some-user", GithubUser.class);
        assertNotNull(cachedUser);
        assertEquals("some-user", cachedUser.login());

        // Verify cache has repos (stored as List<GithubRepo>)
        List<GithubRepo> cachedRepos = (List<GithubRepo>) githubRepoCache.get("some-user").get();
        assertNotNull(cachedRepos);
        assertEquals(1, cachedRepos.size());
        assertEquals("repo", cachedRepos.get(0).name());

        // Since we hit the cache, wiremock should have a single count per endpoint
        wireMockServer.verify(1, getRequestedFor(urlEqualTo("/users/some-user")));
        wireMockServer.verify(1, getRequestedFor(urlEqualTo("/users/some-user/repos")));
    }

    @Test
    public void shouldVerifyCacheEvictionOnSuccessfulRetrieval() {
        // This assumes TestCacheConfig expireAfterWrite is short (e.g., 5s)
        Cache githubUserCache = cacheManager.getCache("githubUsers");
        Cache githubRepoCache = cacheManager.getCache("githubUserRepos");

        assertNull(githubUserCache.get("some-user"));
        assertNull(githubRepoCache.get("some-user"));

        stubFor(get(urlEqualTo("/users/some-user"))
                .willReturn(okJson("{\"login\":\"some-user\"}")));

        stubFor(get(urlEqualTo("/users/some-user/repos"))
                .willReturn(okJson("[{\"name\":\"repo\",\"url\":\"example.com\"}]")));

        githubController.retrieveGithubUser("some-user");

        TestUtils.awaitUntilNull(githubUserCache, "some-user", Duration.ofSeconds(10));
        TestUtils.awaitUntilNull(githubRepoCache, "some-user", Duration.ofSeconds(10));

        githubController.retrieveGithubUser("some-user");

        // After eviction, both endpoints should be called again
        wireMockServer.verify(2, getRequestedFor(urlEqualTo("/users/some-user")));
        wireMockServer.verify(2, getRequestedFor(urlEqualTo("/users/some-user/repos")));
    }

    @Test
    public void shouldNotFindUserOrAssociatedRepos() {

        stubFor(get(urlPathEqualTo("/users/nonexistent-user"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"User not found\"}")));

        stubFor(get(urlPathEqualTo("/users/nonexistent-user/repos"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Repos not found\"}")));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                githubController.retrieveGithubUser("nonexistent-user"));

        assertTrue(containsCause(ex, HttpClientErrorException.NotFound.class),
                "Expected NotFound in cause chain, but got: " + ex.getClass());

        wireMockServer.verify(1, getRequestedFor(urlPathEqualTo("/users/nonexistent-user")));
        wireMockServer.verify(1, getRequestedFor(urlPathEqualTo("/users/nonexistent-user/repos")));
    }

    @Test
    public void shouldFailDueToServiceErrorAndCachesReposIfReposSucceeds() {
        stubFor(get(urlPathEqualTo("/users/some-user"))
                .willReturn(aResponse().withStatus(500)));

        stubFor(get(urlPathEqualTo("/users/some-user/repos"))
                .willReturn(okJson("[{\"name\":\"repo\",\"url\":\"example.com\"}]")));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                githubController.retrieveGithubUser("some-user"));

        assertTrue(containsCause(ex, HttpServerErrorException.class),
                "Expected HttpServerErrorException in cause chain but got: " + ex.getClass());

        Cache githubUserCache = cacheManager.getCache("githubUsers");
        Cache githubRepoCache = cacheManager.getCache("githubUserRepos");

        assertNull(githubUserCache.get("some-user"), "User should not be cached on failure");
        assertNotNull(githubRepoCache.get("some-user"), "Repos may be cached on partial success");

        wireMockServer.verify(1, getRequestedFor(urlPathEqualTo("/users/some-user")));
        wireMockServer.verify(1, getRequestedFor(urlPathEqualTo("/users/some-user/repos")));
    }


    @Test
    public void shouldRetryOn429ThenSucceedAndCache() {
        // user: 429 then 200
        stubFor(get(urlEqualTo("/users/some-user"))
                .inScenario("retry-user")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(429))
                .willSetStateTo("ok"));

        stubFor(get(urlEqualTo("/users/some-user"))
                .inScenario("retry-user")
                .whenScenarioStateIs("ok")
                .willReturn(okJson("{\"login\":\"some-user\"}")));

        // repos: 429 then 200
        stubFor(get(urlEqualTo("/users/some-user/repos"))
                .inScenario("retry-repos")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(429))
                .willSetStateTo("ok"));

        stubFor(get(urlEqualTo("/users/some-user/repos"))
                .inScenario("retry-repos")
                .whenScenarioStateIs("ok")
                .willReturn(okJson("[{\"name\":\"repo\",\"url\":\"example.com\"}]")));

        GithubUserWithReposResponse result = githubController.retrieveGithubUser("some-user");
        assertEquals("some-user", result.login());
        assertEquals(1, result.repos().size());

        // verify retries happened
        wireMockServer.verify(2, getRequestedFor(urlEqualTo("/users/some-user")));
        wireMockServer.verify(2, getRequestedFor(urlEqualTo("/users/some-user/repos")));

        // second call should be cached (no additional wiremock hits)
        githubController.retrieveGithubUser("some-user");
        wireMockServer.verify(2, getRequestedFor(urlEqualTo("/users/some-user")));
        wireMockServer.verify(2, getRequestedFor(urlEqualTo("/users/some-user/repos")));
    }

    @Test
    public void shouldCallEndpointsInParallel() {
        stubFor(get(urlEqualTo("/users/some-user"))
                .willReturn(aResponse()
                        .withFixedDelay(600)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"login\":\"some-user\"}")));

        stubFor(get(urlEqualTo("/users/some-user/repos"))
                .willReturn(aResponse()
                        .withFixedDelay(600)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"name\":\"repo\",\"url\":\"example.com\"}]")));

        long start = System.nanoTime();
        GithubUserWithReposResponse result = githubController.retrieveGithubUser("some-user");
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertEquals("some-user", result.login());
        assertEquals(1, result.repos().size());

        assertTrue(elapsedMs < 1100, "Expected parallel-ish behavior; elapsedMs=" + elapsedMs);

        wireMockServer.verify(1, getRequestedFor(urlEqualTo("/users/some-user")));
        wireMockServer.verify(1, getRequestedFor(urlEqualTo("/users/some-user/repos")));
    }

    @Test
    void shouldThrowErrorMessageDueToInvalidUsername() {
        Set<?> violations = assertThrows(ConstraintViolationException.class, () ->
                githubController.retrieveGithubUser("--bad-user")
        ).getConstraintViolations();

        violations.forEach(v ->
                assertTrue(v.toString().contains(TestUtils.INVALID_FORMAT_USERNAME))
        );
    }

    @Test
    void shouldThrowErrorMessageDueToUsernameBeingTooLong() {
        String tooLong = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        Set<?> violations = assertThrows(ConstraintViolationException.class, () ->
                githubController.retrieveGithubUser(tooLong)
        ).getConstraintViolations();

        violations.forEach(v ->
                assertTrue(v.toString().contains(TestUtils.INVALID_LENGTH_USERNAME))
        );
    }

    private static boolean containsCause(Throwable t, Class<? extends Throwable> type) {
        while (t != null) {
            if (type.isInstance(t)) return true;
            t = t.getCause();
        }
        return false;
    }
}
