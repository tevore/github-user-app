package com.tevore.service.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.tevore.configuration.TestCacheConfig;
import com.tevore.domain.GithubRepo;
import com.tevore.domain.GithubUser;
import com.tevore.domain.GithubUserWithRepos;
import com.tevore.service.GithubService;
import com.tevore.utils.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;

import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest
@EnableWireMock(@ConfigureWireMock(port = 0)) // dynamic port avoids collisions
@Import(TestCacheConfig.class)
class GithubServiceIntegrationTest {

    @Autowired
    GithubService githubService;

    @Autowired
    CacheManager cacheManager;

    @InjectWireMock
    WireMockServer wireMockServer;

    @BeforeEach
    void setup() {
        wireMockServer.resetAll();

        Cache users = cacheManager.getCache("githubUsers");
        Cache repos = cacheManager.getCache("githubUserRepos");
        assertNotNull(users, "githubUsers cache must exist");
        assertNotNull(repos, "githubUserRepos cache must exist");

        users.clear();
        repos.clear();
    }

    @Test
    void shouldSuccessfullyCacheUserAndRepos() {
        stubFor(get(urlEqualTo("/users/some-user"))
                .willReturn(okJson("{\"login\":\"some-user\"}")));

        stubFor(get(urlEqualTo("/users/some-user/repos"))
                .willReturn(okJson("[{\"name\":\"repo\",\"url\":\"example.com\"}]")));

        GithubUserWithRepos first = githubService.retrieveGithubUserAndRepoInfo("some-user");
        GithubUserWithRepos second = githubService.retrieveGithubUserAndRepoInfo("some-user");

        assertEquals(first.owner().login(), second.owner().login());
        assertEquals(1, second.repos().size());
        assertEquals("repo", second.repos().get(0).name());

        Cache usersCache = cacheManager.getCache("githubUsers");
        Cache reposCache = cacheManager.getCache("githubUserRepos");

        GithubUser cachedUser = usersCache.get("some-user", GithubUser.class);
        assertNotNull(cachedUser);
        assertEquals("some-user", cachedUser.login());

        List<GithubRepo> cachedRepos = (List<GithubRepo>) reposCache.get("some-user").get();
        assertNotNull(cachedRepos);
        assertEquals(1, cachedRepos.size());
        assertEquals("repo", cachedRepos.get(0).name());

        // Verify only one upstream hit per endpoint (second call should be cached)
        wireMockServer.verify(1, getRequestedFor(urlEqualTo("/users/some-user")));
        wireMockServer.verify(1, getRequestedFor(urlEqualTo("/users/some-user/repos")));
    }

    @Test
    void shouldSucceedAndCachesResultsAfter429CausesRetry() {
        stubFor(get(urlEqualTo("/users/some-user"))
                .inScenario("retry-user")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(429))
                .willSetStateTo("ok"));

        stubFor(get(urlEqualTo("/users/some-user"))
                .inScenario("retry-user")
                .whenScenarioStateIs("ok")
                .willReturn(okJson("{\"login\":\"some-user\"}")));

        stubFor(get(urlEqualTo("/users/some-user/repos"))
                .inScenario("retry-repos")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(429))
                .willSetStateTo("ok"));

        stubFor(get(urlEqualTo("/users/some-user/repos"))
                .inScenario("retry-repos")
                .whenScenarioStateIs("ok")
                .willReturn(okJson("[{\"name\":\"repo\",\"url\":\"example.com\"}]")));

        GithubUserWithRepos res = githubService.retrieveGithubUserAndRepoInfo("some-user");

        assertEquals("some-user", res.owner().login());
        assertEquals(1, res.repos().size());

        // Verify retries happened (>= 2 calls to each endpoint)
        wireMockServer.verify(2, getRequestedFor(urlEqualTo("/users/some-user")));
        wireMockServer.verify(2, getRequestedFor(urlEqualTo("/users/some-user/repos")));

        // Verify caching now prevents additional upstream calls
        githubService.retrieveGithubUserAndRepoInfo("some-user");
        wireMockServer.verify(2, getRequestedFor(urlEqualTo("/users/some-user")));
        wireMockServer.verify(2, getRequestedFor(urlEqualTo("/users/some-user/repos")));
    }

    @Test
    void shouldSuccessfullyMakeCallsInParallelBasedOnTiming() {
        // Arrange: delay BOTH endpoints by ~600ms.
        // If parallel, total should be ~600-900ms; if serial, ~1200ms+.
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
        GithubUserWithRepos res = githubService.retrieveGithubUserAndRepoInfo("some-user");
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertEquals("some-user", res.owner().login());
        assertEquals(1, res.repos().size());

        // Threshold chosen to be tolerant of CI noise, but still catch serial behavior.
        // If this fails intermittently in your CI, raise to e.g. 1200ms and keep the delay at 600ms.
        assertTrue(elapsedMs < 1100, "Expected parallel execution; elapsedMs=" + elapsedMs);

        wireMockServer.verify(1, getRequestedFor(urlEqualTo("/users/some-user")));
        wireMockServer.verify(1, getRequestedFor(urlEqualTo("/users/some-user/repos")));
    }

    @Test
    void shouldSuccessfullyConfirmCacheEviction() {
        stubFor(get(urlEqualTo("/users/some-user"))
                .willReturn(okJson("{\"login\":\"some-user\"}")));

        stubFor(get(urlEqualTo("/users/some-user/repos"))
                .willReturn(okJson("[{\"name\":\"repo\",\"url\":\"example.com\"}]")));

        githubService.retrieveGithubUserAndRepoInfo("some-user");

        Cache usersCache = cacheManager.getCache("githubUsers");
        Cache reposCache = cacheManager.getCache("githubUserRepos");

        // Wait until BOTH caches evict (bounded wait; avoids Thread.sleep flakiness)
        TestUtils.awaitUntilNull(usersCache, "some-user", Duration.ofSeconds(10));
        TestUtils.awaitUntilNull(reposCache, "some-user", Duration.ofSeconds(10));

        githubService.retrieveGithubUserAndRepoInfo("some-user");

        wireMockServer.verify(2, getRequestedFor(urlEqualTo("/users/some-user")));
        wireMockServer.verify(2, getRequestedFor(urlEqualTo("/users/some-user/repos")));
    }
}

